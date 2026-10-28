package kr.fintarget.api.domain.user.validation;

import java.time.Year;
import java.time.ZoneId;
import java.util.regex.Pattern;

public class BirthYearValidator {

    public static final int MIN_BIRTH_YEAR = 1940;
    // 올해 기준 이 나이 미만은 가입 대상이 아님 (출생연도 상한 = 올해 - 14)
    public static final int MIN_AGE_OFFSET = 14;
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    // \d 는 유니코드 숫자(전각 등)도 허용할 수 있어 ASCII 숫자만 명시
    private static final Pattern FOUR_DIGITS = Pattern.compile("[0-9]{4}");

    private BirthYearValidator() {
    }

    public static int validate(String rawBirthYear) {
        return validate(rawBirthYear, Year.now(ZONE));
    }

    static int validate(String rawBirthYear, Year currentYear) {
        String trimmed = rawBirthYear.strip();
        if (!FOUR_DIGITS.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("출생연도는 4자리 숫자로 입력해주세요");
        }
        int birthYear = Integer.parseInt(trimmed);
        int maxBirthYear = currentYear.getValue() - MIN_AGE_OFFSET;
        if (birthYear < MIN_BIRTH_YEAR || birthYear > maxBirthYear) {
            throw new IllegalArgumentException(
                    "출생연도는 " + MIN_BIRTH_YEAR + "년부터 " + maxBirthYear + "년까지 입력할 수 있습니다");
        }
        return birthYear;
    }
}
