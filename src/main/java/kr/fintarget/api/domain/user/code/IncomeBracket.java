package kr.fintarget.api.domain.user.code;

import java.util.Arrays;

public enum IncomeBracket {
    UNDER_2M("UNDER_2M", 1_500_000L),
    RANGE_200_300("200_300", 2_500_000L),
    OVER_3M("OVER_3M", 3_500_000L);

    private final String code;
    private final long income;

    IncomeBracket(String code, long income) {
        this.code = code;
        this.income = income;
    }

    public String getCode() {
        return code;
    }

    public long getIncome() {
        return income;
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
