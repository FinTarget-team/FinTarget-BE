package kr.fintarget.api.domain.policy.search.eval;

import kr.fintarget.api.domain.ai.client.AiEmbeddingClient;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.search.PolicySearchHit;
import kr.fintarget.api.domain.policy.search.PolicySearchService;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.SearchCase;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchScorer.CandidatePool;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchScorer.CaseScore;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchScorer.ResultItem;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchScorer.Summary;
import kr.fintarget.api.domain.policy.service.PolicyService;
import kr.fintarget.api.domain.user.entity.User;

import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 검색 평가 실행 (#184 1단계). 운영 코드(PolicyService 후보 필터 + PolicySearchService.searchCandidates)를
 * 스냅샷을 적재한 H2와 메모리 벡터 검색으로 설정값마다 돌리고 {@link PolicySearchScorer}로 채점한다.
 *
 * <ul>
 *   <li>후보 정책은 문항마다 한 번만 계산한다 (설정값과 무관)</li>
 *   <li>설정값마다 PolicySearchService를 새로 만든다. 한도를 차감하지 않는 searchCandidates만 호출하므로 한도 설정·테이블을 쓰지 않는다</li>
 *   <li>{@link SearchConfig#WIDE}(하한 없음, k=20)를 기준으로 하한 손실(같은 k에서 하한 때문에 놓친 정답)을 계산한다</li>
 * </ul>
 */
public final class PolicySearchEvalRunner {

    private final PolicySearchEvalSet evalSet;
    private final PolicySearchSnapshotLoader.Loaded loaded;
    private final PolicyService policyService;
    private final AiEmbeddingClient embeddings;
    private final Map<String, Prepared> prepared = new LinkedHashMap<>();
    private final Map<PolicyKey, String> names = new HashMap<>();

    /**
     * @param embeddings 질문 임베딩 ({@link QuestionEmbeddingCache#client()}. 실호출하지 않는다)
     * @param evalYear   프로필 나이 → 출생연도 계산 기준 연도 (User.ageRange()가 쓰는 올해와 같아야 한다)
     */
    public PolicySearchEvalRunner(PolicySearchEvalSet evalSet, PolicySearchSnapshotLoader.Loaded loaded,
                                  PolicyService policyService, AiEmbeddingClient embeddings, Year evalYear) {
        this.evalSet = evalSet;
        this.loaded = loaded;
        this.policyService = policyService;
        this.embeddings = embeddings;
        loaded.policies().forEach((key, policy) -> names.put(key, policy.getName()));
        for (SearchCase c : evalSet.cases()) {
            User user = evalSet.profileOf(c).toUser("eval-" + c.id(), evalYear);
            List<Policy> candidates = policyService.findCandidatePolicies(user, null);
            int kinfa = (int) candidates.stream()
                    .filter(policy -> PolicySearchScorer.KINFA.equals(loaded.keyOf(policy).source())).count();
            prepared.put(c.id(), new Prepared(c, user, candidates, new CandidatePool(candidates.size(), kinfa)));
        }
    }

    public PolicySearchEvalSet evalSet() {
        return evalSet;
    }

    public PolicySearchSnapshotLoader.Loaded loaded() {
        return loaded;
    }

    /** 문항의 후보 정책 (프로필 조건으로 거른 결과. 나이·소득이 없는 프로필이면 비어 있음) */
    public List<Policy> candidatesOf(SearchCase c) {
        return prepared.get(c.id()).candidates();
    }

    /** 운영 검색 로직을 config로 1회 실행한다. 후보가 없으면 임베딩 없이 빈 목록 (운영과 같음) */
    public List<PolicySearchHit> search(SearchCase c, SearchConfig config) {
        // usageLimiter는 search()에서만 쓰므로 null로 둔다 (searchCandidates는 한도를 차감하지 않음). dailyLimit도 쓰이지 않는다
        PolicySearchService service = new PolicySearchService(policyService, loaded.searchRepository(), embeddings,
                null, config.k(), config.maxDistance(), config.chunkMultiplier(), 1);
        return service.searchCandidates(candidatesFor(c, config), c.question());
    }

    /**
     * config가 KINFA 조건부 제외를 쓰면 후보에서 KINFA를 뺀다 (#184 비교용).
     * 운영 코드는 손대지 않고 후보 목록만 바꿔, 검색 로직 자체는 운영과 같게 유지한다.
     */
    private List<Policy> candidatesFor(SearchCase c, SearchConfig config) {
        List<Policy> candidates = prepared.get(c.id()).candidates();
        if (!config.excludesKinfaFor(c.question())) {
            return candidates;
        }
        return candidates.stream()
                .filter(policy -> !PolicySearchScorer.KINFA.equals(loaded.keyOf(policy).source()))
                .toList();
    }

    /** 검색 결과를 채점용 항목으로 바꾼다 (조건 재계산 포함) */
    public List<ResultItem> toItems(SearchCase c, List<PolicySearchHit> hits) {
        List<ResultItem> items = new ArrayList<>(hits.size());
        for (PolicySearchHit hit : hits) {
            Policy policy = hit.policy();
            EligibilityCheck.Result check = check(c, policy);
            items.add(new ResultItem(loaded.keyOf(policy), policy.getName(), hit.distance(), check.eligible(),
                    check.regionFallback()));
        }
        return items;
    }

    /** 문항 프로필 기준으로 정책 조건을 다시 계산한다 ({@link EligibilityCheck}) */
    public boolean eligible(SearchCase c, Policy policy) {
        return check(c, policy).eligible();
    }

    private EligibilityCheck.Result check(SearchCase c, Policy policy) {
        User user = prepared.get(c.id()).user();
        User.AgeRange ageRange = user.ageRange();
        return EligibilityCheck.check(ageRange.lower(), ageRange.upper(), user.getIncome(), user.getRegion(),
                policy.getMinAge(), policy.getMaxAge(), policy.getIncomeLimit(), policy.getRegion());
    }

    public CaseScore score(SearchCase c, List<ResultItem> items) {
        return PolicySearchScorer.score(c, items, prepared.get(c.id()).pool(), names);
    }

    /** 넓은 검색 1회 + 설정값 조합 전체를 돌린다 */
    public Report run(List<SearchConfig> configs) {
        Map<String, List<ResultItem>> wideResults = new LinkedHashMap<>();
        List<CaseScore> wideScores = new ArrayList<>();
        for (SearchCase c : evalSet.cases()) {
            List<ResultItem> items = toItems(c, search(c, SearchConfig.WIDE));
            wideResults.put(c.id(), items);
            wideScores.add(score(c, items));
        }

        List<ConfigResult> configResults = new ArrayList<>();
        for (SearchConfig config : configs) {
            List<CaseScore> scores = new ArrayList<>();
            double recallSum = 0;
            double wideRecallSum = 0;
            int scored = 0;
            int scoredEmpty = 0;
            int resultCount = 0;
            for (SearchCase c : evalSet.cases()) {
                List<ResultItem> items = toItems(c, search(c, config));
                CaseScore score = score(c, items);
                scores.add(score);
                resultCount += items.size();
                if (score.scored()) {
                    scored++;
                    recallSum += recallAt(items, c, config.k());
                    wideRecallSum += recallAt(wideResults.get(c.id()), c, config.k());
                    if (items.isEmpty()) {
                        scoredEmpty++;
                    }
                }
            }
            Double recall = scored == 0 ? null : recallSum / scored;
            Double wideRecall = scored == 0 ? null : wideRecallSum / scored;
            Double loss = recall == null || wideRecall == null ? null : wideRecall - recall;
            configResults.add(new ConfigResult(config, PolicySearchScorer.summarize(scores), recall, wideRecall,
                    loss, evalSet.cases().isEmpty() ? 0 : (double) resultCount / evalSet.cases().size(),
                    scoredEmpty));
        }
        return new Report(wideResults, wideScores, PolicySearchScorer.summarize(wideScores), configResults);
    }

    /** 상위 k건 안의 정답 수 / 정답 수 */
    static double recallAt(List<ResultItem> ranked, SearchCase c, int k) {
        Set<PolicyKey> relevant = Set.copyOf(c.relevant());
        long found = ranked.subList(0, Math.min(k, ranked.size())).stream()
                .filter(item -> relevant.contains(item.key())).count();
        return (double) found / relevant.size();
    }

    private record Prepared(SearchCase evalCase, User user, List<Policy> candidates, CandidatePool pool) {
    }

    /**
     * @param wideResults 문항 ID → 넓은 검색 결과 (상위 20건)
     * @param configs     설정값별 결과 (입력 순서)
     */
    public record Report(Map<String, List<ResultItem>> wideResults, List<CaseScore> wideScores, Summary wideSummary,
                         List<ConfigResult> configs) {
    }

    /**
     * @param recallAtK     정답 문항 평균 Recall@k (k = 설정값 k, 하한 적용 후)
     * @param wideRecallAtK 같은 k에서 하한이 없을 때의 Recall@k
     * @param loss          하한 때문에 잃은 Recall (wideRecallAtK - recallAtK)
     * @param averageResults 문항당 평균 결과 수 (전체 문항)
     * @param scoredEmpty   정답이 있는 문항인데 결과가 0건인 수
     */
    /**
     * @param recallAtK     Recall@k 평균. 채점 대상 문항이 없으면 null (측정하지 않음)
     * @param wideRecallAtK 하한 없는 검색의 Recall@k. 같은 조건에서 null
     * @param loss          하한 때문에 잃은 Recall(%p 아님, 0~1). 둘 중 하나라도 null이면 null
     */
    public record ConfigResult(SearchConfig config, Summary summary, Double recallAtK, Double wideRecallAtK,
                               Double loss, double averageResults, int scoredEmpty) {

        /** 잠정 목표: Recall@k ≥ 0.9, 하한 손실 ≤ 2%p, 조건 위반 0 */
        public boolean meetsTarget() {
            // 측정하지 않은 조합(채점 대상 0건)은 충족으로 보지 않는다
            return recallAtK != null && loss != null
                    && recallAtK >= 0.9 && loss <= 0.02 + 1e-9 && summary.violations() == 0;
        }
    }
}
