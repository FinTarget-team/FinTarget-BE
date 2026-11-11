package kr.fintarget.api.domain.user.code;

import java.util.Arrays;

/**
 * 주택 보유 여부. NOT_SPECIFIED(선택 안 함)는 User에 null로 저장하고, 응답에서는 null을 NOT_SPECIFIED로 돌려준다.
 * 정책 매칭에는 아직 쓰지 않는다 (정책 쪽 주택 조건 구조화 전).
 */
public enum HousingStatus {
    HOMELESS("무주택"),
    HOMEOWNER("유주택"),
    NOT_SPECIFIED("선택 안 함");

    private final String label;

    HousingStatus(String label) {
        this.label = label;
    }

    public String getCode() {
        return name();
    }

    public String getLabel() {
        return label;
    }

    /** User.housingStatus에 저장할 값. 선택 안 함은 null */
    public String storedValue() {
        return this == NOT_SPECIFIED ? null : name();
    }

    public static HousingStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(status -> status.name().equals(code))
                .findFirst()
                .orElse(null);
    }

    /** 저장값을 응답 코드로 변환. null(선택 안 함 또는 미응답)은 NOT_SPECIFIED */
    public static String responseCodeOf(String storedValue) {
        return storedValue == null ? NOT_SPECIFIED.name() : storedValue;
    }
}
