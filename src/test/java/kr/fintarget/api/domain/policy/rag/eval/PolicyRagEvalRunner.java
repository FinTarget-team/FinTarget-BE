package kr.fintarget.api.domain.policy.rag.eval;

import kr.fintarget.api.domain.ai.chat.service.UserProfileSummary;
import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiEmbeddingClient;
import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.ai.client.AiTextResult;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.rag.PolicyRagPrompt;
import kr.fintarget.api.domain.policy.rag.PolicyRagResult;
import kr.fintarget.api.domain.policy.rag.PolicyRagService;
import kr.fintarget.api.domain.policy.rag.PolicyRagStatus;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalSet.RagCase;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagScorer.Outcome;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagScorer.Score;
import kr.fintarget.api.domain.policy.search.PolicySearchHit;
import kr.fintarget.api.domain.policy.search.PolicySearchService;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchSnapshotLoader;
import kr.fintarget.api.domain.policy.search.eval.SearchConfig;
import kr.fintarget.api.domain.policy.service.PolicyService;
import kr.fintarget.api.domain.user.entity.User;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * 통합 정책 AI 답변 평가 실행 (#184 2단계). 문항을 준비(검색 결과 확정)한 뒤 답변 단계
 * ({@link PolicyRagService#answerWithHits})를 호출하고 {@link PolicyRagScorer}로 채점한다.
 *
 * <ul>
 *   <li>2단계 a ({@link #prepareFixed}): 평가셋에 고정한 검색 결과</li>
 *   <li>2단계 b ({@link #prepareEndToEnd}): 스냅샷 H2에서 운영과 같은 순서로 판단한다 —
 *       나이·소득 없음 → PROFILE_INCOMPLETE, 후보 없음·검색 결과 없음 → NO_EVIDENCE (모두 AI 호출 없음), 그 외 검색 결과로 답변.
 *       PolicyRagService.answer와 같은 순서이며, 검색 결과를 채점에 써야 해서 answerFrom 대신 검색과 답변 단계를 나눠 부른다</li>
 * </ul>
 * 준비(H2 조회·검색)는 테스트 트랜잭션 스레드에서 차례로 하고, AI 호출만 병렬로 한다.
 * 한도를 차감하는 메서드(answer, search)는 부르지 않는다.
 */
public final class PolicyRagEvalRunner {

    /** 운영 기본값 policy.rag.max-output-tokens (PolicyRagService DEFAULT_MAX_OUTPUT_TOKENS) */
    public static final int PRODUCTION_MAX_OUTPUT_TOKENS = 1000;

    private PolicyRagEvalRunner() {
    }

    /** 2단계 a: 평가셋에 고정한 검색 결과 */
    public static List<Prepared> prepareFixed(PolicyRagEvalSet.Bound bound, List<RagCase> cases) {
        return cases.stream().map(c -> {
            List<PolicySearchHit> hits = bound.hitsOf(c);
            return new Prepared(c, hits, c.hits(), bound.profileOf(c), null, List.of());
        }).toList();
    }

    /**
     * 2단계 b: 스냅샷에서 후보 필터와 검색을 운영 코드로 돌린다.
     *
     * @param embeddings 질문 임베딩 (캐시)
     * @param config     검색 설정값 (기본은 운영 값 {@link SearchConfig#PRODUCTION})
     */
    public static List<Prepared> prepareEndToEnd(List<RagCase> cases, PolicySearchEvalSet searchEvalSet,
                                                 PolicySearchSnapshotLoader.Loaded loaded, PolicyService policyService,
                                                 AiEmbeddingClient embeddings, SearchConfig config, Year evalYear) {
        // usageLimiter는 search()에서만 쓰므로 null (searchCandidates는 한도를 차감하지 않음)
        PolicySearchService searchService = new PolicySearchService(policyService, loaded.searchRepository(), embeddings,
                null, config.k(), config.maxDistance(), config.chunkMultiplier(), 1);
        List<Prepared> prepared = new ArrayList<>();
        for (RagCase c : cases) {
            User user = searchEvalSet.profiles().get(c.profile()).toUser("eval-" + c.id(), evalYear);
            if (!PolicyService.hasMatchingProfile(user)) {
                prepared.add(new Prepared(c, List.of(), List.of(), null, PolicyRagStatus.PROFILE_INCOMPLETE, List.of()));
                continue;
            }
            List<Policy> candidates = policyService.findCandidatePolicies(user, null);
            List<PolicySearchHit> hits = searchService.searchCandidates(candidates, c.question());
            List<String> hitKeys = hits.stream().map(hit -> loaded.keyOf(hit.policy()).toString()).toList();
            List<String> searchMissed = c.expectedSources().stream().filter(key -> !hitKeys.contains(key)).toList();
            prepared.add(new Prepared(c, hits, hitKeys, UserProfileSummary.of(user, evalYear),
                    hits.isEmpty() ? PolicyRagStatus.NO_EVIDENCE : null, searchMissed));
        }
        return prepared;
    }

    /**
     * 답변 단계를 호출하고 채점한다. 결과는 입력 순서다.
     *
     * @param keyOf 근거 정책 → 평가셋 정책 키
     */
    public static List<Result> run(List<Prepared> prepared, AiTextClient client, PolicyRagPrompt prompt,
                                   Function<Policy, String> keyOf, int parallelism) throws InterruptedException {
        List<Future<Result>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, parallelism))) {
            for (Prepared p : prepared) {
                futures.add(executor.submit(() -> runOne(p, client, prompt, keyOf)));
            }
        }
        List<Result> results = new ArrayList<>();
        for (Future<Result> future : futures) {
            try {
                results.add(future.get());
            } catch (ExecutionException e) {
                throw new IllegalStateException("평가 실행 실패", e.getCause());
            }
        }
        return results;
    }

    static Result runOne(Prepared p, AiTextClient client, PolicyRagPrompt prompt, Function<Policy, String> keyOf) {
        RagCase c = p.evalCase();
        List<String> hitNames = p.hits().stream().map(hit -> hit.policy().getName()).toList();
        if (p.shortCircuit() != null) {
            Outcome outcome = new Outcome(p.shortCircuit(), null, List.of(), null);
            return new Result(c, prompt.version(), p.hitKeys(), hitNames, p.searchMissed(), p.shortCircuit(), null,
                    List.of(), null, null, 0, 0, 0, 0, null, PolicyRagScorer.score(c, p.hitKeys(), outcome));
        }

        RecordingAiTextClient recording = new RecordingAiTextClient(client);
        // answerWithHits는 생성 클라이언트와 프롬프트만 쓴다. 후보 조회·검색·한도는 쓰지 않아 null로 둔다
        PolicyRagService service = new PolicyRagService(null, null, recording, prompt, null, 1, PRODUCTION_MAX_OUTPUT_TOKENS);
        try {
            PolicyRagResult result = service.answerWithHits(p.hits(), p.profile(), c.question());
            AiTextResult raw = recording.result();
            List<String> sources = result.sources().stream().map(keyOf).toList();
            Outcome outcome = new Outcome(result.status(), result.answer(), sources, raw == null ? null : raw.text());
            return new Result(c, prompt.version(), p.hitKeys(), hitNames, p.searchMissed(), result.status(),
                    result.answer(), sources, outcome.rawAnswer(), raw == null ? null : raw.model(),
                    raw == null ? 0 : raw.inputTokens(), raw == null ? 0 : raw.outputTokens(),
                    raw == null ? 0 : raw.reasoningTokens(), recording.elapsedMs(), null,
                    PolicyRagScorer.score(c, p.hitKeys(), outcome));
        } catch (RuntimeException e) {
            // AI 오류는 사유(TIMEOUT 등)가 메시지보다 원인 파악에 쓸모 있다
            String reason = e instanceof AiClientException aiError ? e.getClass().getSimpleName() + "(" + aiError.getReason() + ")"
                    : e.getClass().getSimpleName();
            Score failed = new Score(c, false, List.of("호출 실패: " + reason), false, true, false, 0, false, 0);
            return new Result(c, prompt.version(), p.hitKeys(), hitNames, p.searchMissed(), null, null, List.of(), null,
                    null, 0, 0, 0, recording.elapsedMs(), reason + ": " + e.getMessage(), failed);
        }
    }

    /**
     * 답변 단계에 넣을 문항 1건.
     *
     * @param hitKeys      검색 결과 정책 키 (순서 = 근거 번호)
     * @param shortCircuit null이 아니면 AI를 호출하지 않고 이 상태로 끝난다 (PROFILE_INCOMPLETE, NO_EVIDENCE)
     * @param searchMissed 기대 근거 중 검색 결과에 없는 정책 (2단계 b. 답변이 아니라 검색이 놓친 것)
     */
    public record Prepared(RagCase evalCase, List<PolicySearchHit> hits, List<String> hitKeys, UserProfileSummary profile,
                           PolicyRagStatus shortCircuit, List<String> searchMissed) {
    }

    /**
     * @param status    결과 상태 (호출 실패면 null)
     * @param answer    서버가 내보내는 답변 (근거 번호 제거·서식 정리 후)
     * @param sources   근거 정책 키 (인용 순서)
     * @param rawAnswer 모델 원문 답변 (근거 번호 포함). AI를 호출하지 않았으면 null. 결과 파일에만 쓴다
     */
    public record Result(RagCase evalCase, String promptVersion, List<String> hitKeys, List<String> hitNames,
                         List<String> searchMissed, PolicyRagStatus status, String answer, List<String> sources,
                         String rawAnswer, String model, int inputTokens, int outputTokens, int reasoningTokens,
                         long elapsedMs, String error, Score score) {
    }
}
