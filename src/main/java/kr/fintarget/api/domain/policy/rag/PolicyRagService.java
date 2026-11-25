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
import kr.fintarget.api.domain.policy.entity.PolicyCondition;
import kr.fintarget.api.domain.policy.recommend.PolicyLocalScope;
import kr.fintarget.api.domain.policy.search.AnswerQueryRewriter;
import kr.fintarget.api.domain.policy.search.PolicySearchHit;
import kr.fintarget.api.domain.policy.search.PolicySearchService;
import kr.fintarget.api.domain.policy.service.PolicyService;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.validation.BirthYearValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.List;

/**
 * 검색 결과 기반 통합 정책 AI 답변 (#183). 질문마다 독립적으로 처리한다 (이전 대화를 반영하지 않음).
 * <ol>
 *   <li>나이와 소득이 모두 없으면 AI 호출 없이 PROFILE_INCOMPLETE (하나만 없으면 그 조건만 건너뛰고 후보를 찾는다, #225)</li>
 *   <li>조건에 맞는 후보에서 시·군·구 한정 정책을 뺀다 (질문에 그 시·군·구 이름이 있으면 남김, #235).
 *       후보가 없으면 AI 호출 없이 NO_EVIDENCE (임베딩도 호출하지 않아 한도를 차감하지 않음)</li>
 *   <li>일일 한도(policy.rag.daily-limit) 1회 차감. 이후 임베딩·생성 호출이 실패하면(502/503) 되돌린다.
 *       한도가 0 이하면 무제한이라 차감하지 않는다 (#239)</li>
 *   <li>후보 안에서 검색({@link PolicySearchService#searchForAnswer}: KINFA 대출 상품만 대출 의도가 없을 때 제외,
 *       질문에 정책명이 있으면 그 정책을 앞에, 검색 한도는 차감하지 않음). 결과가 없으면 NO_EVIDENCE.
 *       원래 질문의 가장 가까운 거리가 멀면 질문을 검색 문장으로 바꿔 한 번 더 검색해 합친다 ({@link PolicyRagQueryRewriter}, #241)</li>
 *   <li>청크를 [1], [2] 번호로 넣어 답을 받고(특정 대상 전용 정책은 "[신청 대상 한정]" 줄을 붙임, 오늘 날짜 포함), 답변의 번호를 근거 정책으로 바꾼다.
 *       유효한 번호가 하나도 없으면 답변을 버리고 NO_EVIDENCE (청크 밖 지식으로 답했을 수 있어 내보내지 않음)</li>
 * </ol>
 *
 * <p>로그에는 질문·답변 본문을 남기지 않는다.
 */
@Slf4j
@Service
public class PolicyRagService {

    // 0이면 무제한 (#239 부스 시연). 다시 걸려면 .env에 POLICY_RAG_DAILY_LIMIT=20처럼 양수를 넣는다
    static final int DEFAULT_DAILY_LIMIT = 0;
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
    // null이면 검색 문장 바꿔 쓰기 없이 원래 질문으로만 검색한다 (평가 실행기)
    private final AnswerQueryRewriter queryRewriter;

    @Autowired
    public PolicyRagService(PolicyService policyService,
                            PolicySearchService searchService,
                            AiTextClient aiTextClient,
                            PolicyRagPrompt prompt,
                            AiUsageLimiter usageLimiter,
                            PolicyRagQueryRewriter queryRewriter,
                            @Value("${policy.rag.daily-limit:" + DEFAULT_DAILY_LIMIT + "}") int dailyLimit,
                            @Value("${policy.rag.max-output-tokens:" + DEFAULT_MAX_OUTPUT_TOKENS + "}") int maxOutputTokens) {
        this(policyService, searchService, aiTextClient, prompt, usageLimiter, (AnswerQueryRewriter) queryRewriter,
                dailyLimit, maxOutputTokens);
    }

    /** 검색 문장 바꿔 쓰기 없이 만든다 (#184 평가 실행기, 기존 테스트) */
    public PolicyRagService(PolicyService policyService, PolicySearchService searchService, AiTextClient aiTextClient,
                            PolicyRagPrompt prompt, AiUsageLimiter usageLimiter, int dailyLimit, int maxOutputTokens) {
        this(policyService, searchService, aiTextClient, prompt, usageLimiter, (AnswerQueryRewriter) null,
                dailyLimit, maxOutputTokens);
    }

    PolicyRagService(PolicyService policyService, PolicySearchService searchService, AiTextClient aiTextClient,
                     PolicyRagPrompt prompt, AiUsageLimiter usageLimiter, AnswerQueryRewriter queryRewriter,
                     int dailyLimit, int maxOutputTokens) {
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
        this.queryRewriter = queryRewriter;
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
        List<Policy> candidates = answerable(policyService.findCandidatePolicies(user, null), question);
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
        // 시·군·구 이름·대출 의도·정책명은 원래 질문으로 판정하고, 바꾼 문장은 검색에만 쓴다 (#241)
        List<Policy> answerable = answerable(candidates, question);
        List<PolicySearchHit> hits = queryRewriter == null
                ? searchService.searchForAnswer(answerable, question)
                : searchService.searchForAnswer(answerable, question, queryRewriter);
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
        String systemPrompt = prompt.render(hits.stream().map(PolicyRagService::promptText).toList(), profile);
        // 답변 생성에는 원래 질문을 넣는다 (바꾼 검색 문장은 넣지 않음, #241)
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

    /**
     * 통합 답변 후보 (#235): 시·군·구 한정 정책({@link PolicyLocalScope})은 뺀다. 온보딩 지역이 시·도 단위라 그 시·군·구에 사는지 알 수 없어서다.
     * 단, 질문에 그 시·군·구 이름이 있으면 남긴다 ({@link LocalPlaceMention}). 추천 목록·정책 검색 API에는 적용하지 않는다.
     */
    static List<Policy> answerable(List<Policy> candidates, String question) {
        return LocalPlaceMention.filter(candidates, question, PolicyLocalScope::isLocal);
    }

    /**
     * 프롬프트에 넣을 청크 본문. 특정 대상 전용 정책(#227 specialTarget)이면 청크 맨 앞에 "[신청 대상 한정] 대상 집단" 줄을 붙여,
     * 모델이 받을 수 있는 정책처럼 안내하지 않고 조건으로만 말하게 한다 (#235).
     */
    static String promptText(PolicySearchHit hit) {
        PolicyCondition condition = hit.policy().getCondition();
        if (condition == null || !condition.isSpecialTarget()) {
            return hit.content();
        }
        String group = condition.getSpecialTargetGroup();
        return "[신청 대상 한정] " + (group == null || group.isBlank() ? "특정 대상" : group.strip()) + "\n" + hit.content();
    }
}
