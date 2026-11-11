package kr.fintarget.api.domain.policy.rag;

import kr.fintarget.api.domain.ai.chat.service.AiAnswerFormatter;
import kr.fintarget.api.domain.ai.chat.service.UserProfileSummary;
import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiMessage;
import kr.fintarget.api.domain.ai.client.AiModelTier;
import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.ai.client.AiTextRequest;
import kr.fintarget.api.domain.ai.client.AiTextResult;
import kr.fintarget.api.domain.ai.usage.AiUsageFeature;
import kr.fintarget.api.domain.ai.usage.AiUsageLimiter;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.search.PolicySearchHit;
import kr.fintarget.api.domain.policy.search.PolicySearchService;
import kr.fintarget.api.domain.policy.service.PolicyService;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.validation.BirthYearValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.List;

/**
 * 검색 결과 기반 통합 정책 AI 답변 (#183). 질문마다 독립적으로 처리한다 (이전 대화를 반영하지 않음).
 * <ol>
 *   <li>나이·소득이 없으면 AI 호출 없이 PROFILE_INCOMPLETE</li>
 *   <li>조건에 맞는 후보 정책이 없으면 AI 호출 없이 NO_EVIDENCE (임베딩도 호출하지 않아 한도를 차감하지 않음)</li>
 *   <li>일일 한도(policy.rag.daily-limit) 1회 차감. 이후 임베딩·생성 호출이 실패하면(502/503) 되돌린다</li>
 *   <li>후보 안에서 검색({@link PolicySearchService#searchCandidates}, 검색 한도는 차감하지 않음). 결과가 없으면 NO_EVIDENCE</li>
 *   <li>청크를 [1], [2] 번호로 넣어 답을 받고, 답변의 번호를 근거 정책으로 바꾼다.
 *       유효한 번호가 하나도 없으면 답변을 버리고 NO_EVIDENCE (청크 밖 지식으로 답했을 수 있어 내보내지 않음)</li>
 * </ol>
 *
 * <p>로그에는 질문·답변 본문을 남기지 않는다.
 */
@Slf4j
@Service
public class PolicyRagService {

    static final int DEFAULT_DAILY_LIMIT = 20;
    // 추론 토큰 포함. 단건 채팅 평균 출력 88·추론 14토큰(docs/ai/eval-results-2026-11)이고 여러 정책을 다뤄도 이 안에 든다.
    // 넘으면 응답이 불완전(INCOMPLETE)으로 끝나 502가 된다
    static final int DEFAULT_MAX_OUTPUT_TOKENS = 1000;

    private final PolicyService policyService;
    private final PolicySearchService searchService;
    private final AiTextClient aiTextClient;
    private final PolicyRagPrompt prompt;
    private final AiUsageLimiter usageLimiter;
    private final int dailyLimit;
    private final int maxOutputTokens;

    public PolicyRagService(PolicyService policyService,
                            PolicySearchService searchService,
                            AiTextClient aiTextClient,
                            PolicyRagPrompt prompt,
                            AiUsageLimiter usageLimiter,
                            @Value("${policy.rag.daily-limit:" + DEFAULT_DAILY_LIMIT + "}") int dailyLimit,
                            @Value("${policy.rag.max-output-tokens:" + DEFAULT_MAX_OUTPUT_TOKENS + "}") int maxOutputTokens) {
        if (dailyLimit < 1) {
            throw new IllegalArgumentException("policy.rag.daily-limit는 1 이상이어야 합니다: " + dailyLimit);
        }
        if (maxOutputTokens < 1) {
            throw new IllegalArgumentException("policy.rag.max-output-tokens는 1 이상이어야 합니다: " + maxOutputTokens);
        }
        this.policyService = policyService;
        this.searchService = searchService;
        this.aiTextClient = aiTextClient;
        this.prompt = prompt;
        this.usageLimiter = usageLimiter;
        this.dailyLimit = dailyLimit;
        this.maxOutputTokens = maxOutputTokens;
    }

    /**
     * @throws kr.fintarget.api.domain.ai.usage.AiUsageLimitExceededException 오늘 한도를 모두 썼을 때 (429)
     * @throws kr.fintarget.api.domain.ai.client.AiUnavailableException      AI 비활성, OpenAI 한도·잔액 초과 (503)
     * @throws kr.fintarget.api.domain.ai.client.AiCallFailedException       임베딩·생성 호출 실패, 시간 초과, 출력 상한 초과 (502)
     */
    public PolicyRagResult answer(String userId, String question) {
        User user = policyService.findUser(userId);
        if (!PolicyService.hasMatchingProfile(user)) {
            log.info("통합 정책 AI 답변 status={}", PolicyRagStatus.PROFILE_INCOMPLETE);
            return PolicyRagResult.profileIncomplete();
        }
        List<Policy> candidates = policyService.findCandidatePolicies(user, null);
        if (candidates.isEmpty()) {
            log.info("통합 정책 AI 답변 status={} candidates=0", PolicyRagStatus.NO_EVIDENCE);
            return PolicyRagResult.noEvidence();
        }

        AiUsageLimiter.Ticket ticket = usageLimiter.acquire(userId, AiUsageFeature.POLICY_RAG, dailyLimit);
        try {
            return answerFrom(user, candidates, question);
        } catch (AiClientException e) {
            usageLimiter.release(ticket);
            throw e;
        }
    }

    /**
     * 후보 → 검색 → 생성 → 근거 변환. 일일 한도를 차감하지 않는다:
     * {@link #answer}가 한도를 차감한 뒤 호출하고, 검색부터 답변까지 평가(#184 2단계 b)는 사용자 요청이 아니라서 직접 호출한다.
     *
     * @param candidates 사용자 조건에 맞는 후보 정책 ({@link PolicyService#findCandidatePolicies(User, String)})
     */
    public PolicyRagResult answerFrom(User user, List<Policy> candidates, String question) {
        List<PolicySearchHit> hits = searchService.searchCandidates(candidates, question);
        if (hits.isEmpty()) {
            log.info("통합 정책 AI 답변 status={} candidates={} hits=0", PolicyRagStatus.NO_EVIDENCE, candidates.size());
            return PolicyRagResult.noEvidence();
        }
        return answerWithHits(hits, UserProfileSummary.of(user, Year.now(BirthYearValidator.ZONE)), question);
    }

    /**
     * 검색 결과 → 생성 → 근거 변환. 검색 결과를 고정한 답변 평가(#184 2단계 a)도 이 메서드를 호출한다.
     * 한도를 차감하지 않는다.
     *
     * @param hits 검색 결과. 목록 순서가 프롬프트의 근거 번호다 (첫 번째 = [1]). 비어 있으면 AI 호출 없이 NO_EVIDENCE
     */
    public PolicyRagResult answerWithHits(List<PolicySearchHit> hits, UserProfileSummary profile, String question) {
        if (hits.isEmpty()) {
            log.info("통합 정책 AI 답변 status={} hits=0", PolicyRagStatus.NO_EVIDENCE);
            return PolicyRagResult.noEvidence();
        }
        String systemPrompt = prompt.render(hits.stream().map(PolicySearchHit::content).toList(), profile);
        AiTextResult result = aiTextClient.generate(new AiTextRequest(
                systemPrompt, List.of(AiMessage.user(question)), AiModelTier.DEFAULT, maxOutputTokens));

        PolicyCitationParser.Result parsed = PolicyCitationParser.parse(result.text(), hits.size());
        // 앱은 일반 텍스트로 표시하므로 마크다운·URL을 지운다 (#147과 같은 후처리)
        String answer = AiAnswerFormatter.format(parsed.text());

        if (!parsed.hasCitation() || answer.isEmpty()) {
            log.warn("통합 정책 AI 답변 근거 번호 없음, NO_EVIDENCE로 대체 promptVersion={} model={} hits={} invalidCitations={} inputTokens={} outputTokens={}",
                    prompt.version(), result.model(), hits.size(), parsed.invalidCount(), result.inputTokens(), result.outputTokens());
            return PolicyRagResult.noEvidence();
        }
        if (parsed.invalidCount() > 0) {
            log.warn("통합 정책 AI 답변 검색 결과에 없는 근거 번호 버림 hits={} invalidCitations={}",
                    hits.size(), parsed.invalidCount());
        }

        List<Policy> sources = parsed.cited().stream().map(number -> hits.get(number - 1).policy()).toList();
        log.info("통합 정책 AI 답변 status={} promptVersion={} model={} hits={} cited={} inputTokens={} outputTokens={}",
                PolicyRagStatus.ANSWERED, prompt.version(), result.model(), hits.size(), sources.size(),
                result.inputTokens(), result.outputTokens());
        return PolicyRagResult.answered(answer, sources);
    }
}
