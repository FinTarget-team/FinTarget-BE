package kr.fintarget.api.domain.policy.benefit;

import kr.fintarget.api.domain.policy.entity.PolicyBenefit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BenefitExtractionTest {

    @Test
    void CASH는_시뮬레이션_입력_형식으로_바뀐다() {
        BenefitExtraction extraction = new BenefitExtraction(BenefitType.CASH, 500_000L, 60, 10_000_000L, false,
                "5년간 매월 50만원", null);

        PolicyBenefit benefit = extraction.toPolicyBenefit();

        assertThat(benefit.getMonthlyAmount()).isEqualTo(500_000L);
        assertThat(benefit.getBenefitMonths()).isEqualTo(60);
        assertThat(benefit.getOneTimeAmount()).isEqualTo(10_000_000L);
        assertThat(benefit.amountForMonth(1)).isEqualTo(10_500_000L);
        assertThat(benefit.amountForMonth(61)).isZero();
    }

    @Test
    void 기간이_없는_월_지급은_기간_null로_저장되고_계산에서_12개월만_반영된다() {
        PolicyBenefit benefit = new BenefitExtraction(BenefitType.CASH, 500_000L, null, null, false, "매월 50만원", null)
                .toPolicyBenefit();

        assertThat(benefit.getBenefitMonths()).isNull();
        assertThat(benefit.amountForMonth(12)).isEqualTo(500_000L);
        assertThat(benefit.amountForMonth(13)).isZero();
    }

    @Test
    void CASH가_아닌_유형은_시뮬레이션에_반영하지_않는다() {
        for (BenefitType type : BenefitType.values()) {
            if (type == BenefitType.CASH) {
                continue;
            }
            assertThat(BenefitExtraction.of(type, null, null).toPolicyBenefit()).as(type.name()).isNull();
        }
    }
}
