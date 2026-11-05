package kr.fintarget.api.domain.policy.search.eval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 비교할 검색 설정값 1조합 (#184). PolicySearchService의 policy.search.k, max-distance, chunk-multiplier와 같다.
 */
public record SearchConfig(int k, double maxDistance, int chunkMultiplier) {

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
        return String.format(Locale.ROOT, "k=%d d=%.2f m=%d", k, maxDistance, chunkMultiplier);
    }

    private static List<String> csv(String name, String defaultValue) {
        String value = System.getenv(name);
        String raw = value == null || value.isBlank() ? defaultValue : value;
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
