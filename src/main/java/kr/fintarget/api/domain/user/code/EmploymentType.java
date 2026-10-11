package kr.fintarget.api.domain.user.code;

import java.util.Arrays;

public enum EmploymentType {
    EMPLOYED,
    UNEMPLOYED,
    STUDENT,
    SELF_EMPLOYED;

    public String getCode() {
        return name();
    }

    public static EmploymentType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.name().equals(code))
                .findFirst()
                .orElse(null);
    }
}
