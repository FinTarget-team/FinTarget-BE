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
    void 출생연도가_있으면_올해_빼기_출생연도_빼기_1로_계산한다() {
        User user = user().birthYear(2000).build();

        assertThat(user.ageIn(Year.of(2026))).isEqualTo(25);
        assertThat(user.ageIn(Year.of(2027))).isEqualTo(26);
    }

    @Test
    void 출생연도가_있으면_저장된_age보다_우선한다() {
        User user = user().age(40).birthYear(2000).build();

        assertThat(user.ageIn(Year.of(2026))).isEqualTo(25);
    }

    @Test
    void 출생연도가_없으면_저장된_age로_폴백한다() {
        assertThat(user().age(25).build().ageIn(Year.of(2026))).isEqualTo(25);
        assertThat(user().age(40).build().ageIn(Year.of(2026))).isEqualTo(40);
    }

    @Test
    void 출생연도와_age가_모두_없으면_null이다() {
        assertThat(user().build().getAge()).isNull();
    }

    @Test
    void getAge는_Asia_Seoul_올해를_기준으로_계산한다() {
        User user = user().birthYear(2000).build();
        int expected = Year.now(BirthYearValidator.ZONE).getValue() - 2000 - 1;

        assertThat(user.getAge()).isEqualTo(expected);
    }

    @Test
    void updateBirthYear로_출생연도를_변경한다() {
        User user = user().age(25).build();

        user.updateBirthYear(1990);

        assertThat(user.getBirthYear()).isEqualTo(1990);
        assertThat(user.ageIn(Year.of(2026))).isEqualTo(35);
    }
}
