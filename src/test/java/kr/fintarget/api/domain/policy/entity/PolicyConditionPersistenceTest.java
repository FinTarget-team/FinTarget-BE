package kr.fintarget.api.domain.policy.entity;

import kr.fintarget.api.domain.policy.condition.BenefitKind;
import kr.fintarget.api.domain.policy.condition.ConditionExtraction;
import kr.fintarget.api.domain.policy.condition.ConditionSource;
import kr.fintarget.api.domain.policy.condition.HousingRequirement;
import kr.fintarget.api.domain.policy.condition.IncomeBasis;
import kr.fintarget.api.domain.policy.condition.TargetType;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 추출 조건(#220)과 신청기간 구분 코드 컬럼이 DB에 저장·조회되고, 매일 동기화(updateFrom)가 추출 결과를 지우지 않는지 확인한다.
 */
@DataJpaTest
class PolicyConditionPersistenceTest {

    private static final String VERSION = "policy-condition-v1";

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Policy reload(Policy policy) {
        entityManager.flush();
        entityManager.clear();
        return policyRepository.findById(policy.getPolicyId()).orElseThrow();
    }

    private static Policy youthPolicy(String externalId, String eligibility) {
        return Policy.create("월세 지원", "설명", null, null, null, null, PolicyType.HOUSING, null, externalId,
                "YOUTH_CENTER", PolicyText.builder().eligibilityDetail(eligibility).applyPeriodCode("0057002").build());
    }

    @Test
    void 추출한_조건의_모든_컬럼과_신청기간_구분_코드를_저장하고_다시_읽는다() {
        Policy saved = policyRepository.save(youthPolicy("EXT-C1", "무주택 청년"));
        String hash = ConditionSource.of(saved).contentHash();
        saved.updateCondition(new ConditionExtraction(IncomeBasis.INDIVIDUAL, 4_166_666L,
                HousingRequirement.HOMELESS_REQUIRED, TargetType.BOTH, List.of("JEONNAM", "GWANGJU"))
                .toPolicyCondition(VERSION, hash, LocalDateTime.now()));

        Policy found = reload(saved);
        PolicyCondition condition = found.getCondition();

        assertThat(condition.getIncomeBasis()).isEqualTo(IncomeBasis.INDIVIDUAL);
        assertThat(condition.getIncomeMaxMonthly()).isEqualTo(4_166_666L);
        assertThat(condition.getHousingRequirement()).isEqualTo(HousingRequirement.HOMELESS_REQUIRED);
        assertThat(condition.getTargetType()).isEqualTo(TargetType.BOTH);
        assertThat(condition.extractedRegionCodes()).containsExactly("JEONNAM", "GWANGJU");
        assertThat(condition.getPromptVersion()).isEqualTo(VERSION);
        assertThat(condition.getSourceHash()).isEqualTo(hash);
        assertThat(condition.getExtractedAt()).isNotNull();
        assertThat(found.getText().getApplyPeriodCode()).isEqualTo("0057002");
    }

    @Test
    void 추출하지_않은_정책은_조건이_null이다() {
        Policy found = reload(policyRepository.save(youthPolicy("EXT-C2", "무주택 청년")));

        assertThat(found.getCondition()).isNull();
        assertThat(found.needsConditionExtraction(ConditionSource.of(found).contentHash(), VERSION)).isTrue();
    }

    @Test
    void 동기화로_원문을_갱신해도_추출_결과는_유지되고_원문이_바뀌었으면_다시_추출_대상이_된다() {
        Policy saved = policyRepository.save(youthPolicy("EXT-C3", "무주택 청년"));
        saved.updateCondition(ConditionExtraction.unknown().toPolicyCondition(VERSION,
                ConditionSource.of(saved).contentHash(), LocalDateTime.now()));
        Policy stored = reload(saved);

        stored.updateFrom(youthPolicy("EXT-C3", "무주택 세대주"));
        Policy found = reload(stored);

        assertThat(found.getText().getEligibilityDetail()).isEqualTo("무주택 세대주");
        assertThat(found.getCondition()).isNotNull();
        assertThat(found.needsConditionExtraction(ConditionSource.of(found).contentHash(), VERSION)).isTrue();
    }

    @Test
    void 동기화로_같은_원문을_다시_받으면_다시_추출하지_않는다() {
        Policy saved = policyRepository.save(youthPolicy("EXT-C4", "무주택 청년"));
        saved.updateCondition(ConditionExtraction.unknown().toPolicyCondition(VERSION,
                ConditionSource.of(saved).contentHash(), LocalDateTime.now()));
        Policy stored = reload(saved);

        stored.updateFrom(youthPolicy("EXT-C4", "무주택 청년"));
        Policy found = reload(stored);

        assertThat(found.needsConditionExtraction(ConditionSource.of(found).contentHash(), VERSION)).isFalse();
    }

    @Test
    void 경제적_혜택_유형을_저장하고_없으면_UNKNOWN으로_읽는다() {
        Policy saved = policyRepository.save(youthPolicy("EXT-C5", "무주택 청년"));
        saved.updateCondition(new ConditionExtraction(IncomeBasis.NONE, null, HousingRequirement.NONE, TargetType.INDIVIDUAL,
                List.of(), BenefitKind.COST_SAVING, true).toPolicyCondition(VERSION, ConditionSource.of(saved).contentHash(),
                LocalDateTime.now()));
        Policy v1 = policyRepository.save(youthPolicy("EXT-C6", "무주택 세대주"));
        v1.updateCondition(PolicyCondition.builder().incomeBasis(IncomeBasis.NONE).promptVersion("policy-condition-v1").build());

        assertThat(reload(saved).getCondition().getBenefitKind()).isEqualTo(BenefitKind.COST_SAVING);
        assertThat(reload(saved).getCondition().getLocalScope()).isTrue();
        assertThat(reload(v1).getCondition().getLocalScope()).isNull();
        assertThat(reload(v1).getCondition().benefitKindOrUnknown()).isEqualTo(BenefitKind.UNKNOWN);
    }

    @Test
    void 시군구_한정_정형값을_저장하고_동기화_때_새_값으로_바꾼다() {
        Policy saved = policyRepository.save(youthPolicy("EXT-C7", "무주택 청년").withRegionLocalScope(true));
        Policy stored = reload(saved);
        assertThat(stored.getRegionLocalScope()).isTrue();

        stored.updateFrom(youthPolicy("EXT-C7", "무주택 청년").withRegionLocalScope(false));

        assertThat(reload(stored).getRegionLocalScope()).isFalse();
    }
}
