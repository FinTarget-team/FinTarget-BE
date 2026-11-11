package kr.fintarget.api.domain.user.code;

import java.util.Arrays;

/**
 * 세전 월 소득 구간. 하한(minIncome) 포함, 상한(maxIncome) 미포함이며 마지막 구간의 상한은 null(제한 없음).
 * 경계(150/260/300/380/410만원)는 온통청년·KINFA 정책 소득 조건 분석(2026-10, #87)에서
 * 구간별 '확인 필요' 정책 수의 최댓값이 가장 작은 10만원 단위 조합으로 정했다.
 * income은 서버 내부 저장용 대표값이며 구버전 대표값(1,500,000 / 2,500,000 / 3,500,000)과 겹치지 않게 둔다.
 */
public enum IncomeBracket {
    UNDER_150("UNDER_150", 1_000_000L, 0L, 1_500_000L),
    RANGE_150_260("150_260", 2_050_000L, 1_500_000L, 2_600_000L),
    RANGE_260_300("260_300", 2_800_000L, 2_600_000L, 3_000_000L),
    RANGE_300_380("300_380", 3_400_000L, 3_000_000L, 3_800_000L),
    RANGE_380_410("380_410", 3_950_000L, 3_800_000L, 4_100_000L),
    OVER_410("OVER_410", 4_500_000L, 4_100_000L, null);

    private final String code;
    private final long income;
    private final long minIncome;
    private final Long maxIncome;

    IncomeBracket(String code, long income, long minIncome, Long maxIncome) {
        this.code = code;
        this.income = income;
        this.minIncome = minIncome;
        this.maxIncome = maxIncome;
    }

    public String getCode() {
        return code;
    }

    public long getIncome() {
        return income;
    }

    public long getMinIncome() {
        return minIncome;
    }

    public Long getMaxIncome() {
        return maxIncome;
    }

    public static IncomeBracket fromCode(String code) {
        return Arrays.stream(values())
                .filter(bracket -> bracket.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    public static IncomeBracket fromIncome(Long income) {
        if (income == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(bracket -> bracket.income == income)
                .findFirst()
                .orElse(null);
    }
}
