package kr.fintarget.api.domain.policy.entity;

import kr.fintarget.api.domain.policy.dto.PolicyResponse;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PolicyTextPersistenceTest {

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private TestEntityManager entityManager;

    private PolicyText fullText(String suffix) {
        return PolicyText.builder()
                .supportContent("지원" + suffix).eligibilityDetail("자격" + suffix).exclusionCriteria("제외" + suffix)
                .incomeCondition("소득" + suffix).ageCondition("나이" + suffix).applyMethod("신청" + suffix)
                .screeningMethod("심사" + suffix).requiredDocuments("서류" + suffix).applyPeriod("신청기간" + suffix)
                .businessPeriod("사업기간" + suffix).referenceUrl("https://ref/" + suffix).applyUrl("https://apply/" + suffix)
                .contactInfo("문의" + suffix).etcNote("기타" + suffix)
                .build();
    }

    @Test
    void 원문을_저장하고_다시_읽으면_모든_컬럼이_그대로다() {
        String longText = "가".repeat(5000);
        Policy saved = policyRepository.save(Policy.create("정책", "설명", null, null, null, null, PolicyType.ETC,
                null, "EXT-1", "YOUTH_CENTER", PolicyText.builder().supportContent(longText).etcNote("기타").build()));
        entityManager.flush();
        entityManager.clear();

        PolicyText text = policyRepository.findById(saved.getPolicyId()).orElseThrow().getText();

        assertThat(text.getSupportContent()).isEqualTo(longText);
        assertThat(text.getEtcNote()).isEqualTo("기타");
        assertThat(text.getApplyUrl()).isNull();
    }

    @Test
    void 원문_없이_만든_정책도_저장되고_조회된다() {
        Policy saved = policyRepository.save(Policy.create("정책", "설명", null, null, null, null, PolicyType.ETC,
                null, "EXT-2", "KINFA"));
        entityManager.flush();
        entityManager.clear();

        Policy found = policyRepository.findById(saved.getPolicyId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("정책");
        assertThat(found.getText() == null || found.getText().getSupportContent() == null).isTrue();
    }

    @Test
    void updateFrom은_원문까지_새_값으로_덮어쓴다() {
        Policy saved = policyRepository.save(Policy.create("정책", "설명", null, null, null, null, PolicyType.ETC,
                null, "EXT-3", "YOUTH_CENTER", fullText("-old")));
        entityManager.flush();
        entityManager.clear();

        Policy stored = policyRepository.findById(saved.getPolicyId()).orElseThrow();
        stored.updateFrom(Policy.create("정책", "설명", null, null, null, null, PolicyType.ETC,
                null, "EXT-3", "YOUTH_CENTER", fullText("-new")));
        entityManager.flush();
        entityManager.clear();

        PolicyText text = policyRepository.findById(saved.getPolicyId()).orElseThrow().getText();
        assertThat(text.getSupportContent()).isEqualTo("지원-new");
        assertThat(text.getScreeningMethod()).isEqualTo("심사-new");
        assertThat(text.getBusinessPeriod()).isEqualTo("사업기간-new");
        assertThat(text.getContactInfo()).isEqualTo("문의-new");
    }

    @Test
    void PolicyResponse에는_원문이_노출되지_않는다() {
        assertThat(Arrays.stream(PolicyResponse.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("policyId", "name", "description", "minAge", "maxAge", "incomeLimit",
                        "benefitAmount", "policyType", "regions");
    }
}
