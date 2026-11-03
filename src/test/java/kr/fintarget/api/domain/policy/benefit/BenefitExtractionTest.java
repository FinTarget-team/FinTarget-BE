package kr.fintarget.api.domain.policy.benefit;

import kr.fintarget.api.domain.policy.entity.PolicyBenefit;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BenefitExtractionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 12, 0);

    private static PolicyBenefit save(BenefitExtraction extraction) {
        return extraction.toPolicyBenefit("policy-benefit-v1", "hash", NOW);
    }

    @Test
    void CASH는_시뮬레이션_입력_형식으로_바뀐다() {
        PolicyBenefit benefit = save(new BenefitExtraction(BenefitType.CASH, 500_000L, 60, 10_000_000L, false,
                "5년간 매월 50만원", null));

        assertThat(benefit.getMonthlyAmount()).isEqualTo(500_000L);
        assertThat(benefit.getBenefitMonths()).isEqualTo(60);
        assertThat(benefit.getOneTimeAmount()).isEqualTo(10_000_000L);
        assertThat(benefit.hasAny()).isTrue();
        assertThat(benefit.amountForMonth(1)).isEqualTo(10_500_000L);
        assertThat(benefit.amountForMonth(61)).isZero();
    }

    @Test
    void 추출_정보를_함께_저장한다() {
        PolicyBenefit benefit = save(new BenefitExtraction(BenefitType.CASH, 200_000L, 12, null, true, "월 최대 20만원", null));

        assertThat(benefit.getType()).isEqualTo(BenefitType.CASH);
        assertThat(benefit.isMaximum()).isTrue();
        assertThat(benefit.getEvidence()).isEqualTo("월 최대 20만원");
        assertThat(benefit.getPromptVersion()).isEqualTo("policy-benefit-v1");
        assertThat(benefit.getSourceHash()).isEqualTo("hash");
        assertThat(benefit.getExtractedAt()).isEqualTo(NOW);
        assertThat(benefit.isExtractedFrom("hash", "policy-benefit-v1")).isTrue();
        assertThat(benefit.isExtractedFrom("other", "policy-benefit-v1")).isFalse();
        assertThat(benefit.isExtractedFrom("hash", "policy-benefit-v2")).isFalse();
    }

    @Test
    void 기간이_없는_월_지급은_기간_null로_저장되고_계산에서_12개월만_반영된다() {
        PolicyBenefit benefit = save(new BenefitExtraction(BenefitType.CASH, 500_000L, null, null, false, "매월 50만원", null));

        assertThat(benefit.getBenefitMonths()).isNull();
        assertThat(benefit.amountForMonth(12)).isEqualTo(500_000L);
        assertThat(benefit.amountForMonth(13)).isZero();
    }

    @Test
    void CASH가_아닌_유형은_저장하되_시뮬레이션에_반영하지_않는다() {
        for (BenefitType type : BenefitType.values()) {
            if (type == BenefitType.CASH) {
                continue;
            }
            PolicyBenefit benefit = save(BenefitExtraction.of(type, "근거", null));

            assertThat(benefit.getType()).as(type.name()).isEqualTo(type);
            assertThat(benefit.hasAny()).as(type.name()).isFalse();
            assertThat(benefit.getMonthlyAmount()).as(type.name()).isNull();
            assertThat(benefit.getOneTimeAmount()).as(type.name()).isNull();
            assertThat(benefit.isMaximum()).as(type.name()).isFalse();
        }
    }
}
