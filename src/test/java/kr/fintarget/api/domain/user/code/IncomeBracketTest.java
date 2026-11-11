package kr.fintarget.api.domain.user.code;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IncomeBracketTest {

    @ParameterizedTest
    @CsvSource({
            "UNDER_150, UNDER_150, 1000000",
            "150_260, RANGE_150_260, 2050000",
            "260_300, RANGE_260_300, 2800000",
            "300_380, RANGE_300_380, 3400000",
            "380_410, RANGE_380_410, 3950000",
            "OVER_410, OVER_410, 4500000"
    })
    void 코드로_소득구간을_조회하면_저장값을_반환한다(String code, IncomeBracket expected, long income) {
        assertThat(IncomeBracket.fromCode(code)).isEqualTo(expected);
        assertThat(IncomeBracket.fromCode(code).getIncome()).isEqualTo(income);
    }

    @Test
    void 저장값으로_소득구간을_조회하면_코드를_반환한다() {
        for (IncomeBracket bracket : IncomeBracket.values()) {
            assertThat(IncomeBracket.fromIncome(bracket.getIncome())).isEqualTo(bracket);
            assertThat(IncomeBracket.fromIncome(bracket.getIncome()).getCode()).isEqualTo(bracket.getCode());
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "UNDER_150, 0, 1500000",
            "RANGE_150_260, 1500000, 2600000",
            "RANGE_260_300, 2600000, 3000000",
            "RANGE_300_380, 3000000, 3800000",
            "RANGE_380_410, 3800000, 4100000",
            "OVER_410, 4100000, null"
    }, nullValues = "null")
    void 구간의_하한과_상한을_반환한다(IncomeBracket bracket, long minIncome, Long maxIncome) {
        assertThat(bracket.getMinIncome()).isEqualTo(minIncome);
        assertThat(bracket.getMaxIncome()).isEqualTo(maxIncome);
    }

    @Test
    void 구간은_빈틈과_겹침_없이_이어지고_마지막_구간만_상한이_없다() {
        IncomeBracket[] brackets = IncomeBracket.values();
        assertThat(brackets[0].getMinIncome()).isZero();
        for (int i = 0; i < brackets.length - 1; i++) {
            assertThat(brackets[i].getMaxIncome()).isEqualTo(brackets[i + 1].getMinIncome());
        }
        assertThat(brackets[brackets.length - 1].getMaxIncome()).isNull();
    }

    @Test
    void 대표값은_자기_구간의_하한_이상_상한_미만이다() {
        for (IncomeBracket bracket : IncomeBracket.values()) {
            assertThat(bracket.getIncome()).isGreaterThanOrEqualTo(bracket.getMinIncome());
            if (bracket.getMaxIncome() != null) {
                assertThat(bracket.getIncome()).isLessThan(bracket.getMaxIncome());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {1_500_000L, 2_500_000L, 3_500_000L})
    void 구간_개편_전_대표값은_구간_미확정으로_null을_반환한다(long legacyIncome) {
        assertThat(IncomeBracket.fromIncome(legacyIncome)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNDER_2M", "200_300", "OVER_3M"})
    void 구간_개편_전_코드는_null을_반환한다(String legacyCode) {
        assertThat(IncomeBracket.fromCode(legacyCode)).isNull();
    }

    @Test
    void 매핑에_없는_코드나_값이면_null을_반환하고_예외를_던지지_않는다() {
        assertThat(IncomeBracket.fromCode("UNKNOWN")).isNull();
        assertThat(IncomeBracket.fromCode(null)).isNull();
        assertThat(IncomeBracket.fromCode("")).isNull();
        assertThat(IncomeBracket.fromIncome(999L)).isNull();
    }

    @Test
    void income이_null이면_null을_반환한다() {
        assertThat(IncomeBracket.fromIncome(null)).isNull();
    }
}
