package kr.fintarget.api.domain.user.validation;

public class NameValidator {

    private NameValidator() {
    }

    public static String validate(String rawName) {
        String trimmed = rawName.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("이름은 비어 있을 수 없습니다");
        }
        if (trimmed.length() > 20) {
            throw new IllegalArgumentException("이름은 20자 이하여야 합니다");
        }
        return trimmed;
    }
}
