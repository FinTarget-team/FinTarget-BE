package kr.fintarget.api.domain.user.entity;

import kr.fintarget.api.domain.user.validation.BirthYearValidator;
import org.junit.jupiter.api.Test;

import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgeTest {

    private User.UserBuilder user() {
        return User.builder().id("user-1").provider("KAKAO").providerId("kakao-1");
    }

    @Test
    void 출생연도가_있으면_생일_전후_두_만_나이를_구간으로_계산한다() {
        User user = user().birthYear(2000).build();

        assertThat(user.ageRangeIn(Year.of(2026))).isEqualTo(new User.AgeRange(25, 26));
        assertThat(user.ageRangeIn(Year.of(2027))).isEqualTo(new User.AgeRange(26, 27));
    }

    @Test
    void 출생연도가_있으면_저장된_age보다_우선한다() {
        User user = user().age(40).birthYear(2000).build();

        assertThat(user.ageRangeIn(Year.of(2026))).isEqualTo(new User.AgeRange(25, 26));
    }

    @Test
    void 출생연도가_없으면_저장된_age로_한_점_구간을_만든다() {
        assertThat(user().age(25).build().ageRangeIn(Year.of(2026))).isEqualTo(new User.AgeRange(25, 25));
        assertThat(user().age(40).build().ageRangeIn(Year.of(2026))).isEqualTo(new User.AgeRange(40, 40));
    }

    @Test
    void 출생연도와_age가_모두_없으면_null이다() {
        assertThat(user().build().ageRange()).isNull();
    }

    @Test
    void ageRange는_Asia_Seoul_올해를_기준으로_계산한다() {
        User user = user().birthYear(2000).build();
        int thisYear = Year.now(BirthYearValidator.ZONE).getValue();

        assertThat(user.ageRange()).isEqualTo(new User.AgeRange(thisYear - 2000 - 1, thisYear - 2000));
    }

    @Test
    void updateBirthYear로_출생연도를_변경하면_구간도_바뀐다() {
        User user = user().age(25).build();

        user.updateBirthYear(1990);

        assertThat(user.getBirthYear()).isEqualTo(1990);
        assertThat(user.ageRangeIn(Year.of(2026))).isEqualTo(new User.AgeRange(35, 36));
    }
}
