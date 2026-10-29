package kr.fintarget.api.domain.ai.chat.service;

import kr.fintarget.api.domain.user.code.EmploymentType;
import kr.fintarget.api.domain.user.code.HousingStatus;
import kr.fintarget.api.domain.user.code.IncomeBracket;
import kr.fintarget.api.domain.user.code.Region;
import kr.fintarget.api.domain.user.entity.User;

import java.time.Year;

/**
 * AI 프롬프트에 넣을 사용자 정보 5개 (나이 구간, 소득 구간, 지역, 주택 보유 여부, 취업 상태).
 * 출생연도·소득 원래 값은 넣지 않고 구간만 넣는다. 값이 없거나 구간을 정할 수 없으면 "확인 필요".
 */
public record UserProfileSummary(String age, String income, String region, String housing, String employment) {

    static final String UNKNOWN = "확인 필요";

    public static UserProfileSummary of(User user, Year currentYear) {
        return new UserProfileSummary(
                ageOf(user, currentYear),
                incomeOf(user.getIncome()),
                regionOf(user.getRegion()),
                housingOf(user.getHousingStatus()),
                employmentOf(user.getEmploymentType()));
    }

    public String toPromptText() {
        return "- 나이: " + age + "\n"
                + "- 세전 월 소득: " + income + "\n"
                + "- 거주 지역: " + region + "\n"
                + "- 주택 보유: " + housing + "\n"
                + "- 취업 상태: " + employment;
    }

    // 월일을 받지 않아 만 나이를 두 값 중 하나로만 알 수 있다 (User.ageRange 참고).
    // 출생연도 도입 전(#87) 연령대 대표값(25/40)은 구간이 아니라서 쓰지 않는다
    private static String ageOf(User user, Year currentYear) {
        if (user.getBirthYear() == null) {
            return UNKNOWN;
        }
        int ageAfterBirthday = currentYear.getValue() - user.getBirthYear();
        return "만 " + (ageAfterBirthday - 1) + "세 또는 만 " + ageAfterBirthday + "세 (생일 전후에 따라 다름)";
    }

    // 구간 개편 전 대표값(1,500,000 등)은 fromIncome이 null을 돌려주므로 확인 필요
    private static String incomeOf(Long income) {
        IncomeBracket bracket = IncomeBracket.fromIncome(income);
        if (bracket == null) {
            return UNKNOWN;
        }
        String min = manwon(bracket.getMinIncome());
        if (bracket.getMaxIncome() == null) {
            return min + " 이상";
        }
        String max = manwon(bracket.getMaxIncome());
        return bracket.getMinIncome() == 0 ? max + " 미만" : min + " 이상 " + max + " 미만";
    }

    private static String regionOf(String code) {
        Region region = Region.fromCode(code);
        return region == null ? UNKNOWN : region.getLabel();
    }

    // 선택 안 함·미응답은 null로 저장된다 (HousingStatus 참고)
    private static String housingOf(String code) {
        HousingStatus status = HousingStatus.fromCode(code);
        return status == null || status == HousingStatus.NOT_SPECIFIED ? UNKNOWN : status.getLabel();
    }

    // 라벨은 온보딩 선택지(OnboardingService)와 같다
    private static String employmentOf(String code) {
        EmploymentType type = EmploymentType.fromCode(code);
        if (type == null) {
            return UNKNOWN;
        }
        return switch (type) {
            case EMPLOYED -> "직장인";
            case UNEMPLOYED -> "구직 중";
            case STUDENT -> "학생";
            case SELF_EMPLOYED -> "자영업자";
        };
    }

    private static String manwon(long won) {
        return (won / 10_000) + "만원";
    }
}
