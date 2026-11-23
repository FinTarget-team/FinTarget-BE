package kr.fintarget.api.domain.ai.chat.service;

import kr.fintarget.api.domain.user.code.EmploymentType;
import kr.fintarget.api.domain.user.code.HousingStatus;
import kr.fintarget.api.domain.user.code.IncomeBracket;
import kr.fintarget.api.domain.user.code.Region;
import kr.fintarget.api.domain.user.entity.User;

import java.time.Year;
import java.util.Locale;

/**
 * AI 프롬프트에 넣을 사용자 정보 5개 (나이 구간, 소득 구간, 지역, 주택 보유 여부, 취업 상태).
 * 출생연도·소득 원래 값은 넣지 않고 구간만 넣는다. 소득은 월 소득 구간과 연소득 환산 구간을 함께 쓴다 (#216).
 * 값이 없거나 구간을 정할 수 없으면 "확인 필요".
 *
 * @param incomeMedianRatio 1인 가구 기준 중위소득 대비 비율 구간 ({@link MedianIncome}). 단건 정책 AI 질문 프롬프트에만 넣는다
 *                          ({@link #toQaPromptText()}). 통합 답변 프롬프트에는 사용 규칙이 없어 넣지 않는다. 알 수 없으면 null
 */
public record UserProfileSummary(String age, String income, String region, String housing, String employment,
                                 String incomeMedianRatio) {

    static final String UNKNOWN = "확인 필요";

    /** 중위소득 비율 없이 만든다. 통합 답변 평가셋처럼 비율을 쓰지 않는 곳과 테스트용 */
    public UserProfileSummary(String age, String income, String region, String housing, String employment) {
        this(age, income, region, housing, employment, null);
    }

    public static UserProfileSummary of(User user, Year currentYear) {
        return new UserProfileSummary(
                ageOf(user, currentYear),
                incomeOf(user.getIncome()),
                regionOf(user.getRegion()),
                housingOf(user.getHousingStatus()),
                employmentOf(user.getEmploymentType()),
                MedianIncome.ratioLabel(IncomeBracket.fromIncome(user.getIncome()), currentYear));
    }

    /** 통합 정책 AI 답변(#183)용 사용자 정보 5줄 */
    public String toPromptText() {
        return "- 나이: " + age + "\n"
                + "- 세전 월 소득: " + income + "\n"
                + "- 거주 지역: " + region + "\n"
                + "- 주택 보유: " + housing + "\n"
                + "- 취업 상태: " + employment;
    }

    /** 단건 정책 AI 질문용 사용자 정보. 5줄에 1인 가구 기준 중위소득 대비 비율을 더한다 (#216) */
    public String toQaPromptText() {
        return toPromptText() + "\n"
                + "- 1인 가구 기준 중위소득 대비: " + (incomeMedianRatio == null ? UNKNOWN : incomeMedianRatio);
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

    // 구간 개편 전 대표값(1,500,000 등)은 fromIncome이 null을 돌려주므로 확인 필요.
    // 원문 소득 조건은 연소득 기준이 많아 월 소득×12 환산 구간을 함께 넣는다 (#216). 모델이 직접 곱하다 틀리지 않도록 서버에서 계산한다
    private static String incomeOf(Long income) {
        IncomeBracket bracket = IncomeBracket.fromIncome(income);
        if (bracket == null) {
            return UNKNOWN;
        }
        Long annualMax = bracket.getMaxIncome() == null ? null : bracket.getMaxIncome() * 12;
        return range(bracket.getMinIncome(), bracket.getMaxIncome())
                + " (연으로 계산하면 " + range(bracket.getMinIncome() * 12, annualMax) + ")";
    }

    private static String range(long min, Long max) {
        if (max == null) {
            return manwon(min) + " 이상";
        }
        return min == 0 ? manwon(max) + " 미만" : manwon(min) + " 이상 " + manwon(max) + " 미만";
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
        return String.format(Locale.ROOT, "%,d만원", won / 10_000);
    }
}
