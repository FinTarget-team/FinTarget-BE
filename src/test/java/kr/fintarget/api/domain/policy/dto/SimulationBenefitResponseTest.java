package kr.fintarget.api.domain.policy.dto;

import kr.fintarget.api.domain.policy.benefit.BenefitExtraction;
import kr.fintarget.api.domain.policy.benefit.BenefitType;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.simulation.estimate.SavingEstimateResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationBenefitResponseTest {

    private static Policy policyWith(BenefitExtraction extraction) {
        Policy policy = Policy.create("정책", null, null, null, null, null, PolicyType.HOUSING, null, "id", "YOUTH_CENTER");
        if (extraction != null) {
            policy.updateBenefit(extraction.toPolicyBenefit("policy-benefit-v1", "hash", LocalDateTime.now()));
        }
        return policy;
    }

    @Test
    void 반영할_혜택이_없으면_null이다() {
        assertThat(SimulationBenefitResponse.from(policyWith(null))).isNull();
        assertThat(SimulationBenefitResponse.from(policyWith(BenefitExtraction.of(BenefitType.LOAN, "근거", null)))).isNull();
    }

    @Test
    void 월_지급과_최대_금액_여부를_내려준다() {
        SimulationBenefitResponse response = SimulationBenefitResponse.from(policyWith(
                new BenefitExtraction(BenefitType.CASH, 200_000L, 24, null, true, "근거", null)));

        assertThat(response.monthlyAmount()).isEqualTo(200_000L);
        assertThat(response.months()).isEqualTo(24);
        assertThat(response.monthsSpecified()).isTrue();
        assertThat(response.oneTimeAmount()).isNull();
        assertThat(response.maximum()).isTrue();
    }

    @Test
    void 기간이_없으면_계산에_쓰는_12개월과_기간_미기재를_함께_내려준다() {
        SimulationBenefitResponse response = SimulationBenefitResponse.from(policyWith(
                new BenefitExtraction(BenefitType.CASH, 500_000L, null, null, false, "근거", null)));

        assertThat(response.months()).isEqualTo(12);
        assertThat(response.monthsSpecified()).isFalse();
    }

    @Test
    void 일회_지급만_있으면_월_항목은_null이다() {
        SimulationBenefitResponse response = SimulationBenefitResponse.from(policyWith(
                new BenefitExtraction(BenefitType.CASH, null, null, 1_000_000L, false, "근거", null)));

        assertThat(response.monthlyAmount()).isNull();
        assertThat(response.months()).isNull();
        assertThat(response.monthsSpecified()).isFalse();
        assertThat(response.oneTimeAmount()).isEqualTo(1_000_000L);
    }

    // ===== 주거 정책 월 절감액 추정 (#257) =====

    @Test
    void 실제_지급액이면_추정_표시가_꺼져_있고_근거가_없다() {
        SimulationBenefitResponse response = SimulationBenefitResponse.from(policyWith(
                new BenefitExtraction(BenefitType.CASH, 200_000L, 24, null, true, "근거", null)));

        assertThat(response.estimated()).isFalse();
        assertThat(response.basis()).isNull();
    }

    @Test
    void 추정값이면_추정_표시와_근거가_있고_기간이_없으면_12개월이다() {
        SimulationBenefitResponse response = SimulationBenefitResponse.fromEstimate(
                new SavingEstimateResult(470_000L, null, "근거 문구"));

        assertThat(response.monthlyAmount()).isEqualTo(470_000L);
        assertThat(response.months()).isEqualTo(12);
        assertThat(response.monthsSpecified()).isFalse();
        assertThat(response.oneTimeAmount()).isNull();
        assertThat(response.maximum()).isFalse();
        assertThat(response.estimated()).isTrue();
        assertThat(response.basis()).isEqualTo("근거 문구");
    }

    @Test
    void 추정값에_기간이_있으면_그_기간을_쓴다() {
        SimulationBenefitResponse response = SimulationBenefitResponse.fromEstimate(
                new SavingEstimateResult(310_000L, 24, "근거"));

        assertThat(response.months()).isEqualTo(24);
        assertThat(response.monthsSpecified()).isTrue();
    }

    @Test
    void 정책_응답은_넘긴_시뮬레이션_혜택을_그대로_쓴다() {
        Policy policy = policyWith(null);
        SimulationBenefitResponse estimate = SimulationBenefitResponse.fromEstimate(
                new SavingEstimateResult(310_000L, null, "근거"));

        assertThat(PolicyResponse.from(policy).simulationBenefit()).isNull();
        assertThat(PolicyResponse.from(policy, estimate).simulationBenefit()).isSameAs(estimate);
    }
}
