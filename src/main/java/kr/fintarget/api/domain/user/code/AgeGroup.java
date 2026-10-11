package kr.fintarget.api.domain.user.code;

import java.util.Arrays;

public enum AgeGroup {
    YOUTH("YOUTH", 25),
    MIDDLE("MIDDLE", 40);

    private final String code;
    private final int age;

    AgeGroup(String code, int age) {
        this.code = code;
        this.age = age;
    }

    public String getCode() {
        return code;
    }

    public int getAge() {
        return age;
    }

    public static AgeGroup fromCode(String code) {
        return Arrays.stream(values())
                .filter(group -> group.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    public static AgeGroup fromAge(Integer age) {
        if (age == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(group -> group.age == age)
                .findFirst()
                .orElse(null);
    }
}
