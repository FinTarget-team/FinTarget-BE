package kr.fintarget.api.domain.ai.chat.service;

import kr.fintarget.api.domain.user.code.IncomeBracket;
import org.junit.jupiter.api.Test;

import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;

class MedianIncomeTest {

    private static final Year Y2026 = Year.of(2026);

    @Test
    void 소득_구간을_1인_가구_기준_중위소득_비율로_바꾸고_하한은_내림_상한은_올림한다() {
        // 2026년 1인 가구 기준 중위소득 2,564,238원
        assertThat(MedianIncome.ratioLabel(IncomeBracket.UNDER_150, Y2026)).isEqualTo("59% 미만");
        assertThat(MedianIncome.ratioLabel(IncomeBracket.RANGE_150_260, Y2026)).isEqualTo("58% 이상 102% 미만");
        assertThat(MedianIncome.ratioLabel(IncomeBracket.RANGE_260_300, Y2026)).isEqualTo("101% 이상 117% 미만");
        assertThat(MedianIncome.ratioLabel(IncomeBracket.RANGE_300_380, Y2026)).isEqualTo("116% 이상 149% 미만");
        assertThat(MedianIncome.ratioLabel(IncomeBracket.RANGE_380_410, Y2026)).isEqualTo("148% 이상 160% 미만");
        assertThat(MedianIncome.ratioLabel(IncomeBracket.OVER_410, Y2026)).isEqualTo("159% 이상");
    }

    @Test
    void 연도별_기준값을_쓴다() {
        // 2027년 1인 가구 기준 중위소득 2,736,042원
        assertThat(MedianIncome.ratioLabel(IncomeBracket.UNDER_150, Year.of(2027))).isEqualTo("55% 미만");
    }

    @Test
    void 기준값이_없는_연도나_구간을_모르면_null() {
        assertThat(MedianIncome.ratioLabel(IncomeBracket.UNDER_150, Year.of(2030))).isNull();
        assertThat(MedianIncome.ratioLabel(null, Y2026)).isNull();
    }
}
