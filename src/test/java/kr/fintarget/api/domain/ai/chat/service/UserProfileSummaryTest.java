package kr.fintarget.api.domain.ai.chat.service;

import kr.fintarget.api.domain.user.code.IncomeBracket;
import kr.fintarget.api.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileSummaryTest {

    private static final Year YEAR = Year.of(2026);

    private static User.UserBuilder base() {
        return User.builder().id("u").provider("kakao").providerId("p");
    }

    @Test
    void 다섯_항목을_구간과_라벨로_요약한다() {
        User user = base().birthYear(2000).income(IncomeBracket.RANGE_150_260.getIncome()).region("SEOUL")
                .housingStatus("HOMELESS").employmentType("EMPLOYED").build();

        UserProfileSummary summary = UserProfileSummary.of(user, YEAR);

        assertThat(summary.age()).isEqualTo("만 25세 또는 만 26세 (생일 전후에 따라 다름)");
        assertThat(summary.income()).isEqualTo("150만원 이상 260만원 미만");
        assertThat(summary.region()).isEqualTo("서울");
        assertThat(summary.housing()).isEqualTo("무주택");
        assertThat(summary.employment()).isEqualTo("직장인");
        assertThat(summary.toPromptText()).contains("- 나이: ", "- 세전 월 소득: ", "- 거주 지역: ", "- 주택 보유: ", "- 취업 상태: ");
    }

    @Test
    void 소득_구간의_양_끝을_표현한다() {
        assertThat(UserProfileSummary.of(base().income(IncomeBracket.UNDER_150.getIncome()).build(), YEAR).income())
                .isEqualTo("150만원 미만");
        assertThat(UserProfileSummary.of(base().income(IncomeBracket.OVER_410.getIncome()).build(), YEAR).income())
                .isEqualTo("410만원 이상");
    }

    @Test
    void 값이_없으면_모두_확인_필요() {
        UserProfileSummary summary = UserProfileSummary.of(base().build(), YEAR);

        assertThat(summary).isEqualTo(new UserProfileSummary("확인 필요", "확인 필요", "확인 필요", "확인 필요", "확인 필요"));
    }

    @Test
    void 구버전_연령대와_소득_대표값_알_수_없는_코드는_확인_필요() {
        User user = base().age(25).income(2_500_000L).region("서울").housingStatus("NOT_SPECIFIED")
                .employmentType("FREELANCER").build();

        UserProfileSummary summary = UserProfileSummary.of(user, YEAR);

        assertThat(summary).isEqualTo(new UserProfileSummary("확인 필요", "확인 필요", "확인 필요", "확인 필요", "확인 필요"));
    }

    @Test
    void 프롬프트에_출생연도와_소득_원래_값을_넣지_않는다() {
        User user = base().birthYear(2000).income(IncomeBracket.RANGE_300_380.getIncome()).build();

        assertThat(UserProfileSummary.of(user, YEAR).toPromptText()).doesNotContain("2000", "3400000", "340만");
    }
}
