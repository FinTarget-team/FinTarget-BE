package kr.fintarget.api.domain.policy.search.eval;

import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.CaseType;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.SearchCase;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchScorer.CandidatePool;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchScorer.CaseScore;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchScorer.ResultItem;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchScorer.Summary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * 가짜 검색 결과로 검색 지표가 맞게 계산되는지 (#184).
 */
class PolicySearchScorerTest {

    private static final PolicyKey A = PolicyKey.parse("YOUTH_CENTER:A");
    private static final PolicyKey B = PolicyKey.parse("YOUTH_CENTER:B");
    private static final PolicyKey C = PolicyKey.parse("KINFA:C");
    private static final PolicyKey D = PolicyKey.parse("KINFA:D");
    private static final PolicyKey BUSAN_FEE = PolicyKey.parse("YOUTH_CENTER:fee-busan");
    private static final PolicyKey SEOUL_FEE = PolicyKey.parse("YOUTH_CENTER:fee-seoul");
    private static final CandidatePool POOL = new CandidatePool(10, 2);
    private static final Map<PolicyKey, String> NAMES = Map.of(A, "정책 A", B, "정책 B", C, "대출 C", D, "대출 D",
            BUSAN_FEE, "보증료 지원", SEOUL_FEE, "보증료 지원");

    /** 정답을 넣으면 라벨링을 마친 문항, 비우면 라벨링 전 문항 */
    private static SearchCase searchCase(String id, CaseType type, List<PolicyKey> relevant) {
        return new SearchCase(id, type, "seoul-25", "질문", relevant, null, !relevant.isEmpty(), null);
    }

    /** 정답이 없는 것이 맞다고 라벨한 문항 (labeled=true, relevant 비움) */
    private static SearchCase labeledEmptyCase(String id, CaseType type) {
        return new SearchCase(id, type, "seoul-25", "질문", List.of(), null, true, null);
    }

    private static ResultItem item(PolicyKey key, double distance) {
        return new ResultItem(key, NAMES.getOrDefault(key, key.externalId()), distance, true, false);
    }

    private static List<ResultItem> ranked(PolicyKey... keys) {
        return java.util.stream.IntStream.range(0, keys.length)
                .mapToObj(i -> item(keys[i], 0.1 + i * 0.05)).toList();
    }

    @Test
    void Recall_at_k는_상위_k건_안의_정답_비율이다() {
        // 정답 A(2위), B(4위) / 결과 C A D B
        CaseScore score = PolicySearchScorer.score(searchCase("c1", CaseType.BENEFIT_SITUATION, List.of(A, B)),
                ranked(C, A, D, B), POOL, NAMES);

        assertThat(score.scored()).isTrue();
        assertThat(score.recall()).containsEntry(3, 0.5).containsEntry(5, 1.0).containsEntry(10, 1.0);
        assertThat(score.firstRelevantRank()).isEqualTo(2);
        assertThat(score.reciprocalRank()).isEqualTo(0.5);
    }

    @Test
    void 정답이_결과에_없으면_Recall과_MRR이_0이다() {
        CaseScore score = PolicySearchScorer.score(searchCase("c1", CaseType.POLICY_NAME, List.of(A)),
                ranked(C, D), POOL, NAMES);

        assertThat(score.recall()).containsEntry(3, 0.0).containsEntry(10, 0.0);
        assertThat(score.reciprocalRank()).isZero();
        assertThat(score.firstRelevantRank()).isNull();
    }

    @Test
    void 이름_단위_Recall은_같은_이름의_다른_지역_정책도_찾은_것으로_본다() {
        // 정답은 서울 건만이지만, 결과에 같은 이름의 부산 건만 있다
        CaseScore score = PolicySearchScorer.score(searchCase("c1", CaseType.REGION, List.of(SEOUL_FEE)),
                List.of(item(BUSAN_FEE, 0.1)), POOL, NAMES);

        assertThat(score.recall()).containsEntry(3, 0.0);
        assertThat(score.nameRecall()).containsEntry(3, 1.0);
    }

    @Test
    void 무관_모호_문항과_라벨_없는_문항은_Recall_계산에서_빠진다() {
        CaseScore unrelated = PolicySearchScorer.score(searchCase("u1", CaseType.UNRELATED, List.of()),
                ranked(A), POOL, NAMES);
        CaseScore unlabeled = PolicySearchScorer.score(searchCase("b1", CaseType.BENEFIT_SITUATION, List.of()),
                ranked(A), POOL, NAMES);
        CaseScore labeled = PolicySearchScorer.score(searchCase("b2", CaseType.BENEFIT_SITUATION, List.of(A)),
                ranked(A), POOL, NAMES);

        assertThat(unrelated.scored()).isFalse();
        assertThat(unrelated.recall()).isEmpty();
        assertThat(unlabeled.scored()).isFalse();

        Summary summary = PolicySearchScorer.summarize(List.of(unrelated, unlabeled, labeled));
        assertThat(summary.cases()).isEqualTo(3);
        assertThat(summary.scoredCases()).isEqualTo(1);
        assertThat(summary.unlabeledCases()).isEqualTo(1);
        assertThat(summary.recall()).containsEntry(5, 1.0);
        assertThat(summary.mrr()).isEqualTo(1.0);
    }

    // 라벨링 전(labeled=false)과 정답 없음이 맞는 문항(labeled=true, relevant 빔)을 구분한다 (#184 2-1)
    @Test
    void 정답_0건으로_라벨한_문항은_라벨링_전과_다르게_센다() {
        CaseScore unlabeled = PolicySearchScorer.score(
                searchCase("b1", CaseType.BENEFIT_SITUATION, List.of()), ranked(A), POOL, NAMES);
        CaseScore labeledEmpty = PolicySearchScorer.score(
                labeledEmptyCase("region-04", CaseType.REGION), ranked(), POOL, NAMES);

        assertThat(unlabeled.evalCase().labeled()).isFalse();
        assertThat(unlabeled.emptyPassed()).isNull();
        assertThat(labeledEmpty.evalCase().labeled()).isTrue();
        assertThat(labeledEmpty.evalCase().labeledEmpty()).isTrue();

        Summary summary = PolicySearchScorer.summarize(List.of(unlabeled, labeledEmpty));
        // 정답 0건 문항은 "라벨링 전"으로 세지 않는다
        assertThat(summary.unlabeledCases()).isEqualTo(1);
        assertThat(summary.labeledEmptyCases()).isEqualTo(1);
    }

    // 정답 0건 문항은 Recall·MRR에서 빠지고 "결과 0건이면 통과"로 채점한다 (#184 2-2)
    @Test
    void 정답_0건_문항은_결과가_없으면_통과하고_있으면_실패한다() {
        CaseScore passed = PolicySearchScorer.score(
                labeledEmptyCase("region-04", CaseType.REGION), ranked(), POOL, NAMES);
        CaseScore failed = PolicySearchScorer.score(
                labeledEmptyCase("region-04", CaseType.REGION), ranked(A), POOL, NAMES);

        assertThat(passed.emptyPassed()).isTrue();
        assertThat(failed.emptyPassed()).isFalse();
        // Recall·MRR 대상이 아니다
        assertThat(passed.scored()).isFalse();
        assertThat(failed.scored()).isFalse();
        assertThat(passed.recall()).isEmpty();

        Summary summary = PolicySearchScorer.summarize(List.of(passed, failed));
        assertThat(summary.scoredCases()).isZero();
        assertThat(summary.labeledEmptyCases()).isEqualTo(2);
        assertThat(summary.labeledEmptyPassed()).isEqualTo(1);
        // 채점 대상이 없으면 Recall·MRR은 null이다 ("측정 안 됨"과 "측정했는데 0"을 구분한다)
        assertThat(summary.scoredCases()).isZero();
        assertThat(summary.recall().get(5)).isNull();
        assertThat(summary.mrr()).isNull();
    }

    // 리포트가 "-"로 적을 수 있게, 측정하지 않은 지표는 null로 돌려준다
    @Test
    void 채점_대상이_없으면_Recall과_MRR은_null이다() {
        Summary empty = PolicySearchScorer.summarize(List.of());
        assertThat(empty.recall().get(5)).isNull();
        assertThat(empty.nameRecall().get(5)).isNull();
        assertThat(empty.mrr()).isNull();

        CaseScore unlabeled = PolicySearchScorer.score(
                searchCase("b1", CaseType.BENEFIT_SITUATION, List.of()), ranked(A), POOL, NAMES);
        Summary onlyUnlabeled = PolicySearchScorer.summarize(List.of(unlabeled));
        assertThat(onlyUnlabeled.recall().get(5)).isNull();
        assertThat(onlyUnlabeled.mrr()).isNull();
        // 유형별 지표도 같다
        assertThat(onlyUnlabeled.byType().get(CaseType.BENEFIT_SITUATION).recallAt5()).isNull();
        assertThat(onlyUnlabeled.byType().get(CaseType.BENEFIT_SITUATION).mrr()).isNull();
    }

    @Test
    void 유형별로_결과_0건_문항_수를_센다() {
        CaseScore withResults = PolicySearchScorer.score(
                searchCase("b1", CaseType.BENEFIT_SITUATION, List.of(A)), ranked(A), POOL, NAMES);
        CaseScore empty = PolicySearchScorer.score(
                searchCase("b2", CaseType.BENEFIT_SITUATION, List.of(A)), ranked(), POOL, NAMES);

        Summary summary = PolicySearchScorer.summarize(List.of(withResults, empty));

        assertThat(summary.byType().get(CaseType.BENEFIT_SITUATION).emptyResultCases()).isEqualTo(1);
        assertThat(summary.byType().get(CaseType.BENEFIT_SITUATION).cases()).isEqualTo(2);
    }

    @Test
    void 조건에_맞지_않는_결과와_지역_전국_처리_건수를_센다() {
        List<ResultItem> results = List.of(
                new ResultItem(A, "정책 A", 0.1, false, false),
                new ResultItem(B, "정책 B", 0.2, true, true),
                new ResultItem(C, "대출 C", 0.3, false, false));

        CaseScore score = PolicySearchScorer.score(searchCase("c1", CaseType.REGION, List.of(B)), results, POOL, NAMES);

        assertThat(score.violations()).containsExactly(A, C);
        assertThat(score.regionFallbacks()).isEqualTo(1);
        Summary summary = PolicySearchScorer.summarize(List.of(score));
        assertThat(summary.violations()).isEqualTo(2);
        assertThat(summary.violationCases()).containsExactly("c1");
    }

    @Test
    void KINFA_비율이_후보_비율의_2배를_넘으면_쏠림이고_대출_질문은_제외한다() {
        // 후보 중 KINFA 20%. 상위 5건 중 KINFA 3건(60%) → 2배(40%) 초과
        List<ResultItem> results = ranked(C, D, A, PolicyKey.parse("KINFA:E"), B);

        CaseScore benefit = PolicySearchScorer.score(searchCase("b1", CaseType.BENEFIT_SITUATION, List.of(A)), results, POOL, NAMES);
        CaseScore loan = PolicySearchScorer.score(searchCase("l1", CaseType.LOAN, List.of(C)), results, POOL, NAMES);
        CaseScore loanIntent = PolicySearchScorer.score(
                new SearchCase("b2", CaseType.BENEFIT_SITUATION, "seoul-25", "질문", List.of(A), true, true, null), results, POOL, NAMES);

        assertThat(benefit.kinfaShareTop()).isCloseTo(0.6, within(1e-9));
        assertThat(benefit.kinfaShareCandidates()).isCloseTo(0.2, within(1e-9));
        assertThat(benefit.kinfaSkewed()).isTrue();
        assertThat(loan.kinfaSkewed()).isFalse();
        assertThat(loanIntent.kinfaSkewed()).isFalse();

        // 정확히 2배(40%)는 쏠림이 아니다
        CaseScore boundary = PolicySearchScorer.score(searchCase("b3", CaseType.BENEFIT_SITUATION, List.of(A)),
                ranked(C, D, A, B, PolicyKey.parse("YOUTH_CENTER:F")), POOL, NAMES);
        assertThat(boundary.kinfaSkewed()).isFalse();
        assertThat(PolicySearchScorer.summarize(List.of(benefit, loan, boundary)).kinfaSkewedCases()).containsExactly("b1");
    }

    @Test
    void 결과나_후보가_없으면_KINFA_비율은_null이고_쏠림이_아니다() {
        CaseScore empty = PolicySearchScorer.score(searchCase("u1", CaseType.UNRELATED, List.of()), List.of(),
                new CandidatePool(0, 0), NAMES);

        assertThat(empty.kinfaShareTop()).isNull();
        assertThat(empty.kinfaShareCandidates()).isNull();
        assertThat(empty.kinfaSkewed()).isFalse();
        assertThat(empty.top1Distance()).isNull();
        assertThat(PolicySearchScorer.summarize(List.of(empty)).unrelatedEmpty()).isEqualTo(1);
    }

    @Test
    void 거리_분포를_정답_정답_아님_무관_1위로_나눠_집계한다() {
        CaseScore labeled = PolicySearchScorer.score(searchCase("b1", CaseType.BENEFIT_SITUATION, List.of(A)),
                List.of(item(A, 0.12), item(B, 0.31), item(C, 0.34)), POOL, NAMES);
        CaseScore unrelated = PolicySearchScorer.score(searchCase("u1", CaseType.UNRELATED, List.of()),
                List.of(item(D, 0.47), item(A, 0.49)), POOL, NAMES);

        Summary summary = PolicySearchScorer.summarize(List.of(labeled, unrelated));

        assertThat(summary.relevantDistanceHistogram()).containsExactly(Map.entry("0.10", 1));
        assertThat(summary.otherDistanceHistogram()).containsExactly(Map.entry("0.30", 2));
        assertThat(summary.unrelatedTop1Histogram()).containsExactly(Map.entry("0.45", 1));
    }

    @Test
    void 구간_경계값은_위_구간에_들어가고_NaN은_따로_센다() {
        assertThat(PolicySearchScorer.histogram(List.of(0.15, 0.1499, 0.0, Double.NaN)))
                .containsEntry("0.15", 1).containsEntry("0.10", 1).containsEntry("0.00", 1).containsEntry("NaN", 1);
    }

    @Test
    void 유형별_요약은_Recall_at_5와_MRR을_낸다() {
        CaseScore name = PolicySearchScorer.score(searchCase("n1", CaseType.POLICY_NAME, List.of(A)), ranked(A), POOL, NAMES);
        CaseScore benefit = PolicySearchScorer.score(searchCase("b1", CaseType.BENEFIT_SITUATION, List.of(B)),
                ranked(A, B), POOL, NAMES);

        Summary summary = PolicySearchScorer.summarize(List.of(name, benefit));

        assertThat(summary.byType().get(CaseType.POLICY_NAME).recallAt5()).isEqualTo(1.0);
        assertThat(summary.byType().get(CaseType.BENEFIT_SITUATION).mrr()).isEqualTo(0.5);
        assertThat(summary.byType()).doesNotContainKey(CaseType.LOAN);
        assertThat(summary.mrr()).isEqualTo(0.75);
    }

    @Test
    void 정답_정책의_이름을_모르면_스냅샷에_없는_키로_보고_거부한다() {
        assertThatThrownBy(() -> PolicySearchScorer.score(
                searchCase("c1", CaseType.POLICY_NAME, List.of(PolicyKey.parse("KINFA:없음"))), ranked(A), POOL, NAMES))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
