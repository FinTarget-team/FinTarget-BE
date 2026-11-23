package kr.fintarget.api.domain.policy.condition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyConditionPromptTest {

    @Test
    void 현재_버전은_v2이고_경제적_혜택_유형_규칙을_담는다() {
        PolicyConditionPrompt prompt = new PolicyConditionPrompt();

        assertThat(PolicyConditionPrompt.VERSION).isEqualTo("policy-condition-v2");
        assertThat(prompt.version()).isEqualTo(PolicyConditionPrompt.VERSION);
        assertThat(prompt.instructions()).contains("benefitKind", "COST_SAVING", "NON_FINANCIAL", "NOT_FOR_INDIVIDUAL",
                "자기돌봄비 연 200만원", "참석 실비", "만남·연결");
    }

    @Test
    void 이전_버전_v1은_비교용으로_계속_읽히고_경제적_혜택_유형이_없다() {
        PolicyConditionPrompt v1 = PolicyConditionPrompt.ofVersion("policy-condition-v1");

        assertThat(v1.version()).isEqualTo("policy-condition-v1");
        assertThat(v1.instructions()).contains("incomeBasis").doesNotContain("benefitKind");
        assertThatThrownBy(() -> PolicyConditionPrompt.ofVersion("../application")).isInstanceOf(IllegalArgumentException.class);
    }
}
