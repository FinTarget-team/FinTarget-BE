package kr.fintarget.api.domain.policy.search.eval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 비교할 검색 설정값 1조합 (#184). k, maxDistance, chunkMultiplier는 PolicySearchService의
 * policy.search.* 와 같다.
 *
 * @param excludeKinfaWithoutLoanIntent 질문에 대출 의도가 없으면 KINFA 정책을 후보에서 빼고 검색한다.
 *                                      운영에는 없는 동작이며 비교용으로만 쓴다 ({@link LoanIntent})
 */
public record SearchConfig(int k, double maxDistance, int chunkMultiplier, boolean excludeKinfaWithoutLoanIntent) {

    /** 기존 3값 조합 (KINFA 제외 없음) */
    public SearchConfig(int k, double maxDistance, int chunkMultiplier) {
        this(k, maxDistance, chunkMultiplier, false);
    }

    /** 현재 운영 기본값 (PolicySearchService DEFAULT_K, DEFAULT_MAX_DISTANCE, DEFAULT_CHUNK_MULTIPLIER) */
    public static final SearchConfig PRODUCTION = new SearchConfig(5, 0.5, 3);
    /**
     * 하한을 사실상 끈 넓은 검색. Recall@10·거리 분포·풀 생성의 기준이다.
     * 코사인 거리는 0~2라 하한 2.0이면 아무것도 자르지 않는다.
     */
    public static final SearchConfig WIDE = new SearchConfig(20, 2.0, 5);

    public static final String ENV_K = "AI_EVAL_SEARCH_K";
    public static final String ENV_MAX_DISTANCE = "AI_EVAL_SEARCH_MAX_DISTANCE";
    public static final String ENV_MULTIPLIER = "AI_EVAL_SEARCH_MULTIPLIER";
    static final String DEFAULT_K = "3,5,7";
    static final String DEFAULT_MAX_DISTANCE = "0.30,0.35,0.40,0.45,0.50,0.55,0.60";
    static final String DEFAULT_MULTIPLIER = "2,3,5";

    public SearchConfig {
        if (k < 1 || chunkMultiplier < 1) {
            throw new IllegalArgumentException("k와 chunkMultiplier는 1 이상이어야 합니다: " + k + ", " + chunkMultiplier);
        }
    }

    /** 모든 조합 (k → 하한 → 배수 순) */
    public static List<SearchConfig> grid(List<Integer> ks, List<Double> maxDistances, List<Integer> multipliers) {
        List<SearchConfig> configs = new ArrayList<>();
        for (int k : ks) {
            for (double maxDistance : maxDistances) {
                for (int multiplier : multipliers) {
                    configs.add(new SearchConfig(k, maxDistance, multiplier));
                }
            }
        }
        return configs;
    }

    /**
     * 돌릴 조합을 정한다. k·하한·배수 환경 변수가 하나라도 있으면 그 값들의 격자를, 없으면
     * {@link #comparisonSet()}(#184에서 비교하기로 한 8조합)을 쓴다.
     */
    public static List<SearchConfig> fromEnv() {
        boolean overridden = System.getenv(ENV_K) != null
                || System.getenv(ENV_MAX_DISTANCE) != null
                || System.getenv(ENV_MULTIPLIER) != null;
        return overridden ? gridFromEnv() : comparisonSet();
    }

    /** 환경 변수(쉼표 구분)로 조합을 만든다. 없으면 k {3,5,7} × 하한 {0.30~0.60, 0.05 간격} × 배수 {2,3,5} */
    public static List<SearchConfig> gridFromEnv() {
        return grid(csv(ENV_K, DEFAULT_K).stream().map(Integer::parseInt).toList(),
                csv(ENV_MAX_DISTANCE, DEFAULT_MAX_DISTANCE).stream().map(Double::parseDouble).toList(),
                csv(ENV_MULTIPLIER, DEFAULT_MULTIPLIER).stream().map(Integer::parseInt).toList());
    }

    public boolean isProduction() {
        return equals(PRODUCTION);
    }

    public String label() {
        return String.format(Locale.ROOT, "k=%d d=%.2f m=%d%s", k, maxDistance, chunkMultiplier,
                excludeKinfaWithoutLoanIntent ? " KINFA조건부" : "");
    }

    /** 이 조합에서 해당 문항의 KINFA를 후보에서 빼야 하는지 */
    public boolean excludesKinfaFor(String question) {
        return excludeKinfaWithoutLoanIntent && !LoanIntent.of(question);
    }

    /**
     * 이슈 #184에서 비교하기로 한 조합.
     * 1) 기준선(운영), 2) 하한만 상향 0.60/0.65/0.70, 3) KINFA 조건부 제외 + 같은 하한 상향
     */
    public static List<SearchConfig> comparisonSet() {
        List<SearchConfig> configs = new ArrayList<>();
        configs.add(PRODUCTION);
        for (double distance : List.of(0.60, 0.65, 0.70)) {
            configs.add(new SearchConfig(PRODUCTION.k(), distance, PRODUCTION.chunkMultiplier()));
        }
        // 기준선 하한(0.50)에도 KINFA 조건부를 적용해, 하한 상향과 KINFA 제외의 효과를 분리해 본다
        for (double distance : List.of(0.50, 0.60, 0.65, 0.70)) {
            configs.add(new SearchConfig(PRODUCTION.k(), distance, PRODUCTION.chunkMultiplier(), true));
        }
        return configs;
    }

    private static List<String> csv(String name, String defaultValue) {
        String value = System.getenv(name);
        String raw = value == null || value.isBlank() ? defaultValue : value;
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
