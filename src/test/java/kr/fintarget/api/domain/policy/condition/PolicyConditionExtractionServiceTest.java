package kr.fintarget.api.domain.policy.condition;

import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyConditionExtractionServiceTest {

    private static final String VERSION = "policy-condition-v1";

    private final PolicyRepository policyRepository = mock(PolicyRepository.class);
    private final PolicyConditionExtractor extractor = mock(PolicyConditionExtractor.class);
    // 단위 테스트에서는 트랜잭션 없이 콜백만 실행한다 (mock 트랜잭션 매니저)
    private final TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
    // 순서가 결과에 영향을 주지 않는지 보려고 병렬 1, 대기 없이
    private final PolicyConditionExtractionService service =
            new PolicyConditionExtractionService(policyRepository, extractor, transactionTemplate, 1, 0);

    @BeforeEach
    void setUp() {
        when(extractor.promptVersion()).thenReturn(VERSION);
    }

    private Policy policy(String name, String eligibility) {
        Policy policy = Policy.create(name, null, null, null, null, null, PolicyType.HOUSING, null, name, "YOUTH_CENTER",
                PolicyText.builder().eligibilityDetail(eligibility).build());
        ReflectionTestUtils.setField(policy, "policyId", UUID.randomUUID());
        when(policyRepository.findById(policy.getPolicyId())).thenReturn(Optional.of(policy));
        return policy;
    }

    private static ConditionExtraction homelessOnly() {
        return new ConditionExtraction(IncomeBasis.NONE, null, HousingRequirement.HOMELESS_REQUIRED, TargetType.INDIVIDUAL,
                List.of());
    }

    @Test
    void 아직_추출하지_않은_정책을_추출해_해시와_버전과_함께_저장한다() {
        Policy policy = policy("월세 지원", "무주택 청년");
        when(policyRepository.findAll()).thenReturn(List.of(policy));
        when(extractor.extract(ConditionSource.of(policy))).thenReturn(new ConditionExtraction(IncomeBasis.INDIVIDUAL,
                3_000_000L, HousingRequirement.HOMELESS_REQUIRED, TargetType.INDIVIDUAL, List.of("JEONNAM", "GWANGJU")));

        ConditionExtractionResult result = service.extractPending();

        assertThat(result.targetCount()).isEqualTo(1);
        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(policy.getCondition().getIncomeMaxMonthly()).isEqualTo(3_000_000L);
        assertThat(policy.getCondition().getExtractedRegion()).isEqualTo("JEONNAM,GWANGJU");
        assertThat(policy.getCondition().getPromptVersion()).isEqualTo(VERSION);
        assertThat(policy.getCondition().getSourceHash()).isEqualTo(ConditionSource.of(policy).contentHash());
        assertThat(policy.getCondition().getExtractedAt()).isNotNull();
    }

    @Test
    void 원문과_프롬프트_버전이_같으면_다시_추출하지_않는다() {
        Policy policy = policy("월세 지원", "무주택 청년");
        policy.updateCondition(homelessOnly().toPolicyCondition(VERSION, ConditionSource.of(policy).contentHash(),
                LocalDateTime.now()));
        when(policyRepository.findAll()).thenReturn(List.of(policy));

        ConditionExtractionResult result = service.extractPending();

        assertThat(result.targetCount()).isZero();
        verify(extractor, never()).extract(any());
    }

    @Test
    void 원문이나_프롬프트_버전이_바뀌면_다시_추출한다() {
        Policy changedSource = policy("월세 지원", "무주택 세대주");
        changedSource.updateCondition(homelessOnly().toPolicyCondition(VERSION, "이전 원문 해시", LocalDateTime.now()));
        Policy changedVersion = policy("전세 지원", "무주택 청년");
        changedVersion.updateCondition(homelessOnly().toPolicyCondition("policy-condition-v0",
                ConditionSource.of(changedVersion).contentHash(), LocalDateTime.now()));
        when(policyRepository.findAll()).thenReturn(List.of(changedSource, changedVersion));
        when(extractor.extract(any())).thenReturn(ConditionExtraction.unknown());

        ConditionExtractionResult result = service.extractPending();

        assertThat(result.targetCount()).isEqualTo(2);
        verify(extractor, times(2)).extract(any());
        assertThat(changedVersion.getCondition().getPromptVersion()).isEqualTo(VERSION);
    }

    @Test
    void all이면_이미_추출한_정책도_다시_추출한다() {
        Policy policy = policy("월세 지원", "무주택 청년");
        policy.updateCondition(homelessOnly().toPolicyCondition(VERSION, ConditionSource.of(policy).contentHash(),
                LocalDateTime.now()));
        when(policyRepository.findAll()).thenReturn(List.of(policy));
        when(extractor.extract(any())).thenReturn(homelessOnly());

        assertThat(service.extractAll().targetCount()).isEqualTo(1);
        verify(extractor).extract(any());
    }

    @Test
    void 응답을_해석하지_못하면_모든_조건을_UNKNOWN으로_저장한다() {
        Policy policy = policy("월세 지원", "무주택 청년");
        when(policyRepository.findAll()).thenReturn(List.of(policy));
        when(extractor.extract(any())).thenThrow(new ConditionExtractionException("JSON 없음"));

        ConditionExtractionResult result = service.extractPending();

        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(result.unparsedCount()).isEqualTo(1);
        assertThat(policy.getCondition().getIncomeBasis()).isEqualTo(IncomeBasis.UNKNOWN);
        assertThat(policy.getCondition().getHousingRequirement()).isEqualTo(HousingRequirement.UNKNOWN);
        assertThat(policy.getCondition().getTargetType()).isEqualTo(TargetType.UNKNOWN);
        assertThat(policy.getCondition().getSourceHash()).isEqualTo(ConditionSource.of(policy).contentHash());
    }

    @Test
    void AI_호출이_실패하면_저장하지_않고_다음_정책을_계속_추출한다() {
        Policy failed = policy("실패 정책", "무주택 청년");
        Policy ok = policy("성공 정책", "무주택 세대주");
        when(policyRepository.findAll()).thenReturn(List.of(failed, ok));
        when(extractor.extract(ConditionSource.of(failed))).thenThrow(AiClientException.of(AiClientException.Reason.TIMEOUT));
        when(extractor.extract(ConditionSource.of(ok))).thenReturn(homelessOnly());

        ConditionExtractionResult result = service.extractPending();

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(failed.getCondition()).isNull();
        assertThat(ok.getCondition()).isNotNull();
    }

    @Test
    void AI를_쓸_수_없으면_남은_정책을_건너뛴다() {
        Policy first = policy("첫 정책", "무주택 청년");
        Policy second = policy("둘째 정책", "무주택 세대주");
        when(policyRepository.findAll()).thenReturn(List.of(first, second));
        when(extractor.extract(any())).thenThrow(AiClientException.of(AiClientException.Reason.QUOTA_EXCEEDED));

        ConditionExtractionResult result = service.extractPending();

        assertThat(result.skippedCount()).isEqualTo(2);
        assertThat(result.savedCount()).isZero();
        verify(extractor, times(1)).extract(any());
    }

    @Test
    void 추출하는_동안_삭제된_정책은_저장하지_않는다() {
        Policy policy = policy("월세 지원", "무주택 청년");
        when(policyRepository.findAll()).thenReturn(List.of(policy));
        when(policyRepository.findById(policy.getPolicyId())).thenReturn(Optional.empty());
        when(extractor.extract(any())).thenReturn(homelessOnly());

        ConditionExtractionResult result = service.extractPending();

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(policy.getCondition()).isNull();
    }

    @Test
    void 이전_프롬프트_버전으로_추출한_정책은_다시_추출하고_경제적_혜택_유형을_저장한다() {
        // #225·#227: 이전 버전으로 추출된 정책은 버전이 달라 현재 VERSION(v3)으로 다시 추출된다
        when(extractor.promptVersion()).thenReturn(PolicyConditionPrompt.VERSION);
        Policy policy = policy("월세 지원", "무주택 청년");
        policy.updateCondition(homelessOnly().toPolicyCondition("policy-condition-v1",
                ConditionSource.of(policy).contentHash(), LocalDateTime.now()));
        when(policyRepository.findAll()).thenReturn(List.of(policy));
        when(extractor.extract(any())).thenReturn(new ConditionExtraction(IncomeBasis.NONE, null,
                HousingRequirement.HOMELESS_REQUIRED, TargetType.INDIVIDUAL, List.of(), BenefitKind.MONEY));

        ConditionExtractionResult result = service.extractPending();

        assertThat(result.targetCount()).isEqualTo(1);
        assertThat(policy.getCondition().getPromptVersion()).isEqualTo("policy-condition-v3");
        assertThat(policy.getCondition().getBenefitKind()).isEqualTo(BenefitKind.MONEY);
    }
}
