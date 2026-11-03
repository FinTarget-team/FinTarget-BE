package kr.fintarget.api.domain.policy.dto;

import kr.fintarget.api.domain.policy.benefit.BenefitExtraction;
import kr.fintarget.api.domain.policy.benefit.BenefitType;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
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
}
