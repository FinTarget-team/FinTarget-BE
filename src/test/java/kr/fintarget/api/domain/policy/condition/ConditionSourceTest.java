package kr.fintarget.api.domain.policy.condition;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionSourceTest {

    private static Policy policy(String region, String description, PolicyText text) {
        return Policy.create("청년 월세 지원", description, 19, 34, null, null, PolicyType.HOUSING, region, "P-1",
                "YOUTH_CENTER", text);
    }

    @Test
    void 자격_판단에_쓰는_필드를_우선순위_순서로_넣고_서류_심사_링크는_넣지_않는다() {
        PolicyText text = PolicyText.builder()
                .supportContent("월 20만원")
                .eligibilityDetail("무주택 청년")
                .incomeCondition("연소득 5천만원 이하")
                .contactInfo("화순군청")
                .requiredDocuments("주민등록등본")
                .screeningMethod("서류 심사")
                .applyUrl("https://apply.example.com")
                .build();

        String prompt = ConditionSource.of(policy(null, "월세 지원 사업", text)).toPromptText();

        assertThat(prompt).startsWith("[정책명]\n청년 월세 지원\n\n[등록된 신청 가능 지역]\n전국\n\n[정책 설명]\n월세 지원 사업");
        assertThat(prompt.indexOf("[자격 조건]")).isLessThan(prompt.indexOf("[소득 조건]"));
        assertThat(prompt.indexOf("[소득 조건]")).isLessThan(prompt.indexOf("[지원 내용]"));
        assertThat(prompt.indexOf("[지원 내용]")).isLessThan(prompt.indexOf("[문의처]"));
        assertThat(prompt).doesNotContain("주민등록등본", "서류 심사", "https://");
    }

    @Test
    void 필드와_전체_길이_상한을_넘으면_뒤쪽부터_자른다() {
        PolicyText text = PolicyText.builder()
                .eligibilityDetail("가".repeat(5_000))
                .exclusionCriteria("나".repeat(5_000))
                .incomeCondition("다".repeat(5_000))
                .supportContent("라".repeat(5_000))
                .contactInfo("문의처는 잘려야 함")
                .build();

        String prompt = ConditionSource.of(policy(null, null, text)).toPromptText();

        assertThat(prompt.length()).isLessThanOrEqualTo(ConditionSource.MAX_TOTAL_LENGTH + 1);
        assertThat(prompt).contains("가".repeat(ConditionSource.MAX_FIELD_LENGTH) + "…")
                .doesNotContain("가".repeat(ConditionSource.MAX_FIELD_LENGTH + 1))
                .doesNotContain("문의처는 잘려야 함");
    }

    @Test
    void 원문이_같으면_해시가_같고_바뀌면_달라진다() {
        PolicyText text = PolicyText.builder().eligibilityDetail("무주택 청년").build();
        PolicyText changed = PolicyText.builder().eligibilityDetail("무주택 세대주").build();

        String hash = ConditionSource.of(policy(null, "설명", text)).contentHash();

        assertThat(ConditionSource.of(policy(null, "설명", text)).contentHash()).isEqualTo(hash).hasSize(64);
        assertThat(ConditionSource.of(policy(null, "설명", changed)).contentHash()).isNotEqualTo(hash);
        assertThat(ConditionSource.of(policy("SEOUL", "설명", text)).contentHash()).isNotEqualTo(hash);
    }

    @Test
    void 정책명과_지역_표시_외에_원문이_없으면_판단할_내용이_없다() {
        assertThat(ConditionSource.of(policy(null, null, null)).hasContent()).isFalse();
        assertThat(ConditionSource.of(policy(null, "설명", null)).hasContent()).isTrue();
    }
}
