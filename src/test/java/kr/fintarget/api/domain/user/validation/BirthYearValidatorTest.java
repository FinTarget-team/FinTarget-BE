package kr.fintarget.api.domain.user.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BirthYearValidatorTest {

    private static final Year YEAR_2026 = Year.of(2026);

    @Test
    void null이면_NullPointerException이_발생한다() {
        assertThatThrownBy(() -> BirthYearValidator.validate(null, YEAR_2026))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void 하한_1940년은_허용된다() {
        assertThat(BirthYearValidator.validate("1940", YEAR_2026)).isEqualTo(1940);
    }

    @Test
    void 상한_올해_빼기_14년은_허용된다() {
        assertThat(BirthYearValidator.validate("2012", YEAR_2026)).isEqualTo(2012);
    }

    @Test
    void 하한보다_이전이면_예외가_발생한다() {
        assertThatThrownBy(() -> BirthYearValidator.validate("1939", YEAR_2026))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("출생연도는 1940년부터 2012년까지 입력할 수 있습니다");
    }

    @Test
    void 상한보다_이후면_예외가_발생한다() {
        assertThatThrownBy(() -> BirthYearValidator.validate("2013", YEAR_2026))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("출생연도는 1940년부터 2012년까지 입력할 수 있습니다");
    }

    @Test
    void 상한은_기준_연도에_따라_바뀐다() {
        assertThat(BirthYearValidator.validate("2013", Year.of(2027))).isEqualTo(2013);
    }

    @Test
    void 앞뒤_공백은_제거된다() {
        assertThat(BirthYearValidator.validate("  2000  ", YEAR_2026)).isEqualTo(2000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "95", "20000", "2000년", "1995-03-01", "abcd", "+200", "２０００", "١٩٩٥"})
    void 아스키_숫자_4자리가_아니면_예외가_발생한다(String value) {
        assertThatThrownBy(() -> BirthYearValidator.validate(value, YEAR_2026))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("출생연도는 4자리 숫자로 입력해주세요");
    }

    @Test
    void 기준_연도_없이_호출하면_Asia_Seoul_올해를_기준으로_한다() {
        int maxBirthYear = Year.now(BirthYearValidator.ZONE).getValue() - BirthYearValidator.MIN_AGE_OFFSET;

        assertThat(BirthYearValidator.validate(String.valueOf(maxBirthYear))).isEqualTo(maxBirthYear);
        assertThatThrownBy(() -> BirthYearValidator.validate(String.valueOf(maxBirthYear + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
