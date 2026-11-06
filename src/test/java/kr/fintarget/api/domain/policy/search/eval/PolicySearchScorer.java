package kr.fintarget.api.domain.policy.search.eval;

import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.CaseType;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.SearchCase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;

/**
 * 정책 검색 평가 채점 (#184 1단계). 검색 결과(정책 단위, 거리순)와 평가셋 정답으로 지표를 계산한다.
 *
 * <ul>
 *   <li>Recall@k: 상위 k건 안의 정답 수 / 정답 수. k = 3, 5, 10</li>
 *   <li>MRR: 첫 정답 순위의 역수 (상위 결과에 정답이 없으면 0)</li>
 *   <li>이름 단위 Recall@k: 정답 정책 이름 중 상위 k건에 같은 이름(공백 무시)이 하나라도 있는 비율 (지역별 같은 이름 정책 보조 지표)</li>
 *   <li>조건 위반: {@link EligibilityCheck}가 부적격으로 본 결과 수 (운영 필터와 같으면 0)</li>
 *   <li>KINFA 쏠림: 대출 의도가 없는 문항에서 상위 {@value #SKEW_TOP}건 중 KINFA 비율이 후보 중 KINFA 비율의 {@value #SKEW_FACTOR}배 초과</li>
 *   <li>거리 분포: 정답 결과·정답 아닌 결과·무관 문항 1위의 거리</li>
 * </ul>
 * Recall·MRR·이름 Recall은 정답이 있는 문항(type이 Recall 대상이고 labeled이며 relevant가 비어 있지 않음)만 평균한다.
 * 정답 0건으로 라벨한 문항(labeled이고 relevant가 빈 문항)은 Recall 대신 "결과 0건이면 통과"로 센다.
 * 목표 기준값(Recall@k ≥ 0.9, KINFA 2배)은 잠정값이다.
 */
public final class PolicySearchScorer {

    public static final List<Integer> RECALL_KS = List.of(3, 5, 10);
    public static final String KINFA = "KINFA";
    public static final int SKEW_TOP = 5;
    public static final double SKEW_FACTOR = 2.0;
    public static final double HISTOGRAM_BIN = 0.05;

    private PolicySearchScorer() {
    }

    /**
     * @param ranked 검색 결과 (정책 단위로 묶인 거리순 목록. k를 최대 10 이상으로 두고 조회한다)
     * @param pool   이 문항 프로필의 후보 정책 수와 그중 KINFA 수
     * @param names  정답 정책 키 → 정책명 (이름 단위 Recall용)
     */
    public static CaseScore score(SearchCase evalCase, List<ResultItem> ranked, CandidatePool pool,
                                  Map<PolicyKey, String> names) {
        // Recall·MRR은 정답이 1건 이상 라벨된 문항만 센다. 정답 0건 문항은 아래 emptyPassed로 따로 채점한다
        boolean scored = evalCase.type().scoredForRecall() && evalCase.hasRelevant();
        Set<PolicyKey> relevant = Set.copyOf(evalCase.relevant());

        Map<Integer, Double> recall = new LinkedHashMap<>();
        Map<Integer, Double> nameRecall = new LinkedHashMap<>();
        Integer firstRelevantRank = null;
        for (int i = 0; i < ranked.size(); i++) {
            if (relevant.contains(ranked.get(i).key())) {
                firstRelevantRank = i + 1;
                break;
            }
        }
        if (scored) {
            Set<String> relevantNames = new LinkedHashSet<>();
            for (PolicyKey key : evalCase.relevant()) {
                String name = names.get(key);
                if (name == null) {
                    throw new IllegalArgumentException("정답 정책의 이름이 없습니다 (스냅샷에 없는 키): " + evalCase.id() + " → " + key);
                }
                relevantNames.add(normalizeName(name));
            }
            for (int k : RECALL_KS) {
                List<ResultItem> top = ranked.subList(0, Math.min(k, ranked.size()));
                long found = top.stream().filter(item -> relevant.contains(item.key())).count();
                recall.put(k, (double) found / relevant.size());
                Set<String> topNames = new LinkedHashSet<>();
                top.forEach(item -> topNames.add(normalizeName(item.name())));
                long foundNames = relevantNames.stream().filter(topNames::contains).count();
                nameRecall.put(k, (double) foundNames / relevantNames.size());
            }
        }
        double reciprocalRank = scored && firstRelevantRank != null ? 1.0 / firstRelevantRank : 0.0;

        List<PolicyKey> violations = ranked.stream().filter(item -> !item.eligible()).map(ResultItem::key).toList();
        int regionFallbacks = (int) ranked.stream().filter(ResultItem::regionFallback).count();

        Double kinfaShareTop = null;
        if (!ranked.isEmpty()) {
            List<ResultItem> top = ranked.subList(0, Math.min(SKEW_TOP, ranked.size()));
            kinfaShareTop = (double) top.stream().filter(item -> KINFA.equals(item.key().source())).count() / top.size();
        }
        Double kinfaShareCandidates = pool.total() == 0 ? null : (double) pool.kinfa() / pool.total();
        boolean kinfaSkewed = !evalCase.isLoanIntent() && kinfaShareTop != null && kinfaShareCandidates != null
                && kinfaShareTop > SKEW_FACTOR * kinfaShareCandidates;

        List<Double> relevantDistances = new ArrayList<>();
        List<Double> otherDistances = new ArrayList<>();
        for (ResultItem item : ranked) {
            (relevant.contains(item.key()) ? relevantDistances : otherDistances).add(item.distance());
        }
        Double top1Distance = ranked.isEmpty() ? null : ranked.getFirst().distance();

        // 정답 없음이 맞다고 라벨한 문항은 결과가 0건이어야 통과다 (조건 위반은 violations로 함께 본다)
        Boolean emptyPassed = evalCase.labeledEmpty() ? ranked.isEmpty() : null;

        return new CaseScore(evalCase, scored, recall, nameRecall, reciprocalRank, firstRelevantRank, violations,
                regionFallbacks, ranked.size(), kinfaShareTop, kinfaShareCandidates, kinfaSkewed, top1Distance,
                relevantDistances, otherDistances, emptyPassed);
    }

    public static Summary summarize(List<CaseScore> scores) {
        List<CaseScore> scored = scores.stream().filter(CaseScore::scored).toList();
        Map<Integer, Double> recall = new LinkedHashMap<>();
        Map<Integer, Double> nameRecall = new LinkedHashMap<>();
        for (int k : RECALL_KS) {
            recall.put(k, mean(scored, s -> s.recall().get(k)));
            nameRecall.put(k, mean(scored, s -> s.nameRecall().get(k)));
        }
        Map<CaseType, TypeSummary> byType = new LinkedHashMap<>();
        for (CaseType type : CaseType.values()) {
            List<CaseScore> ofType = scores.stream().filter(s -> s.evalCase().type() == type).toList();
            if (ofType.isEmpty()) {
                continue;
            }
            List<CaseScore> scoredOfType = ofType.stream().filter(CaseScore::scored).toList();
            byType.put(type, new TypeSummary(ofType.size(), scoredOfType.size(),
                    scoredOfType.isEmpty() ? null : mean(scoredOfType, s -> s.recall().get(5)),
                    scoredOfType.isEmpty() ? null : mean(scoredOfType, CaseScore::reciprocalRank),
                    (int) ofType.stream().filter(s -> s.resultCount() == 0).count()));
        }
        List<Double> relevantDistances = scored.stream().flatMap(s -> s.relevantDistances().stream()).toList();
        List<Double> otherDistances = scored.stream().flatMap(s -> s.otherDistances().stream()).toList();
        List<Double> unrelatedTop1 = scores.stream()
                .filter(s -> s.evalCase().type() == CaseType.UNRELATED && s.top1Distance() != null)
                .map(CaseScore::top1Distance).toList();

        return new Summary(scores.size(), scored.size(),
                (int) scores.stream()
                        .filter(s -> s.evalCase().type().scoredForRecall() && !s.evalCase().labeled()).count(),
                recall, nameRecall, scored.isEmpty() ? 0.0 : mean(scored, CaseScore::reciprocalRank),
                scores.stream().mapToInt(s -> s.violations().size()).sum(),
                scores.stream().filter(s -> !s.violations().isEmpty()).map(s -> s.evalCase().id()).toList(),
                scores.stream().mapToInt(CaseScore::regionFallbacks).sum(),
                scores.stream().filter(CaseScore::kinfaSkewed).map(s -> s.evalCase().id()).toList(),
                (int) scores.stream().filter(s -> s.evalCase().type() == CaseType.UNRELATED && s.resultCount() == 0).count(),
                // 무관 질문 오탐: 결과가 하나라도 나온 UNRELATED 문항 수 (적을수록 좋다)
                (int) scores.stream()
                        .filter(s -> s.evalCase().type() == CaseType.UNRELATED && s.resultCount() > 0).count(),
                // 대출 의도 없는 문항의 상위 SKEW_TOP건 중 KINFA 비율 평균 (결과가 있는 문항만)
                nonLoanKinfaShare(scores),
                (int) scores.stream().filter(s -> s.evalCase().labeledEmpty()).count(),
                (int) scores.stream().filter(s -> Boolean.TRUE.equals(s.emptyPassed())).count(),
                byType, histogram(relevantDistances), histogram(otherDistances), histogram(unrelatedTop1));
    }

    /**
     * 대출 의도가 없는 문항의 KINFA 비율 평균. 결과가 없는 문항은 비율이 없어 제외한다.
     * 문항이 하나도 없으면 null (KINFA를 후보에서 모두 뺀 조합에서는 0.0이 된다)
     */
    private static Double nonLoanKinfaShare(List<CaseScore> scores) {
        List<Double> shares = scores.stream()
                .filter(s -> !s.evalCase().isLoanIntent())
                .map(CaseScore::kinfaShareTop)
                .filter(java.util.Objects::nonNull)
                .toList();
        return shares.isEmpty() ? null : shares.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    /** 0.05 단위 구간별 개수. 키는 구간 하한 ("0.10"은 [0.10, 0.15)). NaN은 "NaN" */
    public static Map<String, Integer> histogram(Collection<Double> distances) {
        Map<String, Integer> bins = new TreeMap<>();
        for (double distance : distances) {
            String bin = Double.isNaN(distance) ? "NaN"
                    : String.format(Locale.ROOT, "%.2f", Math.floor(distance / HISTOGRAM_BIN + 1e-9) * HISTOGRAM_BIN);
            bins.merge(bin, 1, Integer::sum);
        }
        return bins;
    }

    static String normalizeName(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "");
    }

    private static double mean(List<CaseScore> scores, ToDoubleFunction<CaseScore> value) {
        OptionalDouble average = scores.stream().mapToDouble(value).average();
        return average.orElse(0.0);
    }

    /**
     * 검색 결과 1건 (정책 단위)
     *
     * @param eligible       {@link EligibilityCheck} 결과
     * @param regionFallback 지역 값이 코드가 아니라 전국으로 처리된 정책
     */
    public record ResultItem(PolicyKey key, String name, double distance, boolean eligible, boolean regionFallback) {
    }

    /** 후보 정책 수와 그중 KINFA 수 (출처 분포 기준) */
    public record CandidatePool(int total, int kinfa) {
    }

    /**
     * @param scored            Recall·MRR 계산 대상 (Recall 대상 유형이고 정답이 있음)
     * @param recall            k → Recall@k (scored가 아니면 비어 있음)
     * @param nameRecall        k → 이름 단위 Recall@k
     * @param firstRelevantRank 첫 정답 순위 (없으면 null)
     * @param violations        조건에 맞지 않는 결과의 정책 키
     * @param kinfaShareTop     상위 SKEW_TOP건 중 KINFA 비율 (결과가 없으면 null)
     * @param otherDistances    정답이 아닌 결과의 거리
     * @param emptyPassed       정답 0건으로 라벨한 문항의 통과 여부 (결과가 0건이면 true).
     *                          그 외 문항은 null
     */
    public record CaseScore(SearchCase evalCase, boolean scored, Map<Integer, Double> recall,
                            Map<Integer, Double> nameRecall, double reciprocalRank, Integer firstRelevantRank,
                            List<PolicyKey> violations, int regionFallbacks, int resultCount, Double kinfaShareTop,
                            Double kinfaShareCandidates, boolean kinfaSkewed, Double top1Distance,
                            List<Double> relevantDistances, List<Double> otherDistances, Boolean emptyPassed) {
    }

    /**
     * @param cases          전체 문항 수
     * @param scoredCases    Recall 계산에 쓴 문항 수
     * @param unlabeledCases Recall 대상 유형인데 정답이 비어 있는 문항 수 (라벨링 전)
     * @param unrelatedEmpty 무관 문항 중 결과가 0건인 문항 수 (유사도 하한으로 걸러짐)
     */
    /**
     * @param unrelatedWithResults 무관 문항 중 결과가 1건 이상 나온 문항 수 (오탐. 적을수록 좋다)
     * @param nonLoanKinfaShare    대출 의도 없는 문항의 상위 SKEW_TOP건 중 KINFA 비율 평균 (없으면 null)
     * @param labeledEmptyCases    정답 0건으로 라벨한 문항 수
     * @param labeledEmptyPassed   그중 결과가 0건으로 나와 통과한 문항 수
     */
    public record Summary(int cases, int scoredCases, int unlabeledCases, Map<Integer, Double> recall,
                          Map<Integer, Double> nameRecall, double mrr, int violations, List<String> violationCases,
                          int regionFallbacks, List<String> kinfaSkewedCases, int unrelatedEmpty,
                          int unrelatedWithResults, Double nonLoanKinfaShare,
                          int labeledEmptyCases, int labeledEmptyPassed,
                          Map<CaseType, TypeSummary> byType, Map<String, Integer> relevantDistanceHistogram,
                          Map<String, Integer> otherDistanceHistogram, Map<String, Integer> unrelatedTop1Histogram) {
    }

    /**
     * @param recallAt5 Recall@5 평균 (정답 있는 문항이 없으면 null)
     */
    /**
     * @param emptyResultCases 이 유형에서 결과가 0건인 문항 수 (하한에 걸려 아무것도 못 찾은 문항)
     */
    public record TypeSummary(int cases, int scoredCases, Double recallAt5, Double mrr, int emptyResultCases) {
    }
}
