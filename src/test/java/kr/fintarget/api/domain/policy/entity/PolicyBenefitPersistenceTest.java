package kr.fintarget.api.domain.policy.entity;

import kr.fintarget.api.domain.policy.benefit.BenefitExtraction;
import kr.fintarget.api.domain.policy.benefit.BenefitSource;
import kr.fintarget.api.domain.policy.benefit.BenefitType;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 추출 혜택(#162) 컬럼이 DB에 저장·조회되고, 매일 동기화(updateFrom)가 추출 결과를 지우지 않는지 확인한다.
 */
@DataJpaTest
class PolicyBenefitPersistenceTest {

    private static final String VERSION = "policy-benefit-v1";

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Policy reload(Policy policy) {
        entityManager.flush();
        entityManager.clear();
        return policyRepository.findById(policy.getPolicyId()).orElseThrow();
    }

    private static Policy youthPolicy(String externalId, String supportContent) {
        return Policy.create("월세 지원", "설명", null, null, null, null, PolicyType.HOUSING, null, externalId,
                "YOUTH_CENTER", PolicyText.builder().supportContent(supportContent).build());
    }

    @Test
    void 추출한_혜택의_모든_컬럼을_저장하고_다시_읽는다() {
        Policy saved = policyRepository.save(youthPolicy("EXT-B1", "월 최대 20만원씩 12개월 지원"));
        String hash = BenefitSource.of(saved).contentHash();
        LocalDateTime extractedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        saved.updateBenefit(new BenefitExtraction(BenefitType.CASH, 200_000L, 12, 1_000_000L, true,
                "월 최대 20만원씩 12개월 지원", null).toPolicyBenefit(VERSION, hash, extractedAt));

        Policy found = reload(saved);
        PolicyBenefit benefit = found.getBenefit();

        assertThat(benefit.getMonthlyAmount()).isEqualTo(200_000L);
        assertThat(benefit.getBenefitMonths()).isEqualTo(12);
        assertThat(benefit.getOneTimeAmount()).isEqualTo(1_000_000L);
        assertThat(benefit.getType()).isEqualTo(BenefitType.CASH);
        assertThat(benefit.isMaximum()).isTrue();
        assertThat(benefit.getEvidence()).isEqualTo("월 최대 20만원씩 12개월 지원");
        assertThat(benefit.getPromptVersion()).isEqualTo(VERSION);
        assertThat(benefit.getSourceHash()).isEqualTo(hash).hasSize(64);
        assertThat(benefit.getExtractedAt()).isEqualTo(extractedAt);
        assertThat(found.hasBenefitForSimulation()).isTrue();
        // 다시 읽은 원문으로 만든 해시가 같아야 다음 동기화 때 다시 추출하지 않는다
        assertThat(found.needsBenefitExtraction(BenefitSource.of(found).contentHash(), VERSION)).isFalse();
    }

    @Test
    void 반영하지_않는_유형도_저장되어_다시_추출하지_않는다() {
        Policy saved = policyRepository.save(youthPolicy("EXT-B2", "대출이자 최대 300만원 지원"));
        String hash = BenefitSource.of(saved).contentHash();
        saved.updateBenefit(BenefitExtraction.of(BenefitType.LOAN, "대출이자 최대 300만원 지원", null)
                .toPolicyBenefit(VERSION, hash, LocalDateTime.now()));

        Policy found = reload(saved);

        // 금액 컬럼이 모두 null이어도 유형·해시가 있어 임베디드 값이 null로 사라지지 않아야 한다
        assertThat(found.getBenefit()).isNotNull();
        assertThat(found.getBenefit().getType()).isEqualTo(BenefitType.LOAN);
        assertThat(found.hasBenefitForSimulation()).isFalse();
        assertThat(found.needsBenefitExtraction(hash, VERSION)).isFalse();
    }

    @Test
    void 추출하지_않은_정책은_혜택이_없고_추출_대상이다() {
        Policy found = reload(policyRepository.save(youthPolicy("EXT-B3", "월 10만원")));

        assertThat(found.hasBenefitForSimulation()).isFalse();
        assertThat(found.needsBenefitExtraction(BenefitSource.of(found).contentHash(), VERSION)).isTrue();
    }

    @Test
    void 동기화로_원문을_갱신해도_추출_결과는_유지되고_원문이_바뀌었으면_다시_추출_대상이_된다() {
        Policy saved = policyRepository.save(youthPolicy("EXT-B4", "월 최대 20만원씩 12개월 지원"));
        String oldHash = BenefitSource.of(saved).contentHash();
        saved.updateBenefit(new BenefitExtraction(BenefitType.CASH, 200_000L, 12, null, true, "월 최대 20만원씩 12개월 지원",
                null).toPolicyBenefit(VERSION, oldHash, LocalDateTime.now()));
        Policy stored = reload(saved);

        stored.updateFrom(youthPolicy("EXT-B4", "월 최대 30만원씩 12개월 지원"));
        Policy found = reload(stored);

        assertThat(found.getText().getSupportContent()).isEqualTo("월 최대 30만원씩 12개월 지원");
        assertThat(found.getBenefit().getMonthlyAmount()).isEqualTo(200_000L);
        assertThat(found.needsBenefitExtraction(BenefitSource.of(found).contentHash(), VERSION)).isTrue();
    }

    @Test
    void 동기화로_같은_원문을_다시_받으면_다시_추출하지_않는다() {
        Policy saved = policyRepository.save(youthPolicy("EXT-B5", "월 최대 20만원씩 12개월 지원"));
        saved.updateBenefit(new BenefitExtraction(BenefitType.CASH, 200_000L, 12, null, true, "월 최대 20만원씩 12개월 지원",
                null).toPolicyBenefit(VERSION, BenefitSource.of(saved).contentHash(), LocalDateTime.now()));
        Policy stored = reload(saved);

        stored.updateFrom(youthPolicy("EXT-B5", "월 최대 20만원씩 12개월 지원"));
        Policy found = reload(stored);

        assertThat(found.needsBenefitExtraction(BenefitSource.of(found).contentHash(), VERSION)).isFalse();
    }
}
