package kr.fintarget.api.domain.policy.condition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyConditionPromptTest {

    @Test
    void 현재_버전은_v3이고_경제적_혜택_유형과_특정_대상_전용_규칙을_담는다() {
        PolicyConditionPrompt prompt = new PolicyConditionPrompt();

        assertThat(PolicyConditionPrompt.VERSION).isEqualTo("policy-condition-v3");
        assertThat(prompt.version()).isEqualTo(PolicyConditionPrompt.VERSION);
        assertThat(prompt.instructions()).contains("benefitKind", "COST_SAVING", "NON_FINANCIAL", "NOT_FOR_INDIVIDUAL",
                "자기돌봄비 연 200만원", "참석 실비", "만남·연결", "localScope", "특정 시·군·구 거주자");
        assertThat(prompt.instructions()).contains("specialTarget", "specialTargetGroup", "자립준비청년", "신혼·예비부부",
                "중소기업 재직자", "targetType으로 판단", "\"specialTargetGroup\": \"자립준비청년\"");
    }

    @Test
    void 이전_버전_v2는_비교용으로_계속_읽히고_특정_대상_전용_여부가_없다() {
        PolicyConditionPrompt v2 = PolicyConditionPrompt.ofVersion("policy-condition-v2");

        assertThat(v2.instructions()).contains("benefitKind", "localScope").doesNotContain("specialTarget");
    }

    @Test
    void 이전_버전_v1은_비교용으로_계속_읽히고_경제적_혜택_유형이_없다() {
        PolicyConditionPrompt v1 = PolicyConditionPrompt.ofVersion("policy-condition-v1");

        assertThat(v1.version()).isEqualTo("policy-condition-v1");
        assertThat(v1.instructions()).contains("incomeBasis").doesNotContain("benefitKind");
        assertThatThrownBy(() -> PolicyConditionPrompt.ofVersion("../application")).isInstanceOf(IllegalArgumentException.class);
    }
}
