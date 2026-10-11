package kr.fintarget.api.domain.user.code;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IncomeBracketTest {

    @Test
    void 코드로_소득구간을_조회하면_저장값을_반환한다() {
        assertThat(IncomeBracket.fromCode("UNDER_2M")).isEqualTo(IncomeBracket.UNDER_2M);
        assertThat(IncomeBracket.fromCode("UNDER_2M").getIncome()).isEqualTo(1_500_000L);

        assertThat(IncomeBracket.fromCode("200_300")).isEqualTo(IncomeBracket.RANGE_200_300);
        assertThat(IncomeBracket.fromCode("200_300").getIncome()).isEqualTo(2_500_000L);

        assertThat(IncomeBracket.fromCode("OVER_3M")).isEqualTo(IncomeBracket.OVER_3M);
        assertThat(IncomeBracket.fromCode("OVER_3M").getIncome()).isEqualTo(3_500_000L);
    }

    @Test
    void 저장값으로_소득구간을_조회하면_코드를_반환한다() {
        assertThat(IncomeBracket.fromIncome(1_500_000L)).isEqualTo(IncomeBracket.UNDER_2M);
        assertThat(IncomeBracket.fromIncome(1_500_000L).getCode()).isEqualTo("UNDER_2M");

        assertThat(IncomeBracket.fromIncome(2_500_000L)).isEqualTo(IncomeBracket.RANGE_200_300);
        assertThat(IncomeBracket.fromIncome(2_500_000L).getCode()).isEqualTo("200_300");

        assertThat(IncomeBracket.fromIncome(3_500_000L)).isEqualTo(IncomeBracket.OVER_3M);
        assertThat(IncomeBracket.fromIncome(3_500_000L).getCode()).isEqualTo("OVER_3M");
    }

    @Test
    void 매핑에_없는_코드나_값이면_null을_반환하고_예외를_던지지_않는다() {
        assertThat(IncomeBracket.fromCode("UNKNOWN")).isNull();
        assertThat(IncomeBracket.fromCode(null)).isNull();
        assertThat(IncomeBracket.fromIncome(999L)).isNull();
    }

    @Test
    void income이_null이면_null을_반환한다() {
        assertThat(IncomeBracket.fromIncome(null)).isNull();
    }
}
