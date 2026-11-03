package kr.fintarget.api.domain.policy.benefit;

import kr.fintarget.api.domain.ai.client.AiCallFailedException;
import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiUnavailableException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyBenefitExtractionServiceTest {

    private static final String VERSION = "policy-benefit-v1";

    private final PolicyRepository policyRepository = mock(PolicyRepository.class);
    private final PolicyBenefitExtractor extractor = mock(PolicyBenefitExtractor.class);
    // 단위 테스트에서는 트랜잭션 없이 콜백만 실행한다 (mock 트랜잭션 매니저)
    private final TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
    // 순서가 결과에 영향을 주지 않는지 보려고 병렬 1로 고정
    private final PolicyBenefitExtractionService service =
            new PolicyBenefitExtractionService(policyRepository, extractor, transactionTemplate, 1);

    @BeforeEach
    void setUp() {
        when(extractor.promptVersion()).thenReturn(VERSION);
    }

    private Policy policy(String name, String supportContent) {
        Policy policy = Policy.create(name, null, null, null, null, null, PolicyType.HOUSING, null, name, "YOUTH_CENTER",
                PolicyText.builder().supportContent(supportContent).build());
        ReflectionTestUtils.setField(policy, "policyId", UUID.randomUUID());
        when(policyRepository.findById(policy.getPolicyId())).thenReturn(Optional.of(policy));
        return policy;
    }

    private static BenefitExtraction cash(long monthly) {
        return new BenefitExtraction(BenefitType.CASH, monthly, 12, null, true, "근거", null);
    }

    @Test
    void 아직_추출하지_않은_정책을_추출해_저장한다() {
        Policy rent = policy("월세 지원", "월 최대 20만원씩 12개월");
        Policy loan = policy("이자 지원", "대출이자 최대 300만원");
        when(policyRepository.findAll()).thenReturn(List.of(rent, loan));
        when(extractor.extract(BenefitSource.of(rent))).thenReturn(cash(200_000L));
        when(extractor.extract(BenefitSource.of(loan))).thenReturn(BenefitExtraction.of(BenefitType.LOAN, "근거", null));

        BenefitExtractionResult result = service.extractPending();

        assertThat(result.targetCount()).isEqualTo(2);
        assertThat(result.savedByType()).containsEntry(BenefitType.CASH, 1).containsEntry(BenefitType.LOAN, 1);
        assertThat(result.failedCount()).isZero();
        assertThat(rent.hasBenefitForSimulation()).isTrue();
        assertThat(rent.getBenefit().getMonthlyAmount()).isEqualTo(200_000L);
        assertThat(rent.getBenefit().getPromptVersion()).isEqualTo(VERSION);
        assertThat(rent.getBenefit().getSourceHash()).isEqualTo(BenefitSource.of(rent).contentHash());
        // 대출은 반영하지 않지만, 다시 추출하지 않도록 유형은 저장한다
        assertThat(loan.getBenefit().getType()).isEqualTo(BenefitType.LOAN);
        assertThat(loan.hasBenefitForSimulation()).isFalse();
    }

    @Test
    void 원문과_프롬프트_버전이_같으면_다시_추출하지_않는다() {
        Policy policy = policy("월세 지원", "월 최대 20만원씩 12개월");
        policy.updateBenefit(cash(200_000L).toPolicyBenefit(VERSION, BenefitSource.of(policy).contentHash(), LocalDateTime.now()));
        when(policyRepository.findAll()).thenReturn(List.of(policy));

        BenefitExtractionResult result = service.extractPending();

        assertThat(result.targetCount()).isZero();
        verify(extractor, never()).extract(any());
    }

    @Test
    void 원문이_바뀌면_다시_추출한다() {
        Policy policy = policy("월세 지원", "월 최대 30만원씩 12개월");
        policy.updateBenefit(cash(200_000L).toPolicyBenefit(VERSION, "이전 원문 해시", LocalDateTime.now()));
        when(policyRepository.findAll()).thenReturn(List.of(policy));
        when(extractor.extract(any())).thenReturn(cash(300_000L));

        service.extractPending();

        assertThat(policy.getBenefit().getMonthlyAmount()).isEqualTo(300_000L);
    }

    @Test
    void 프롬프트_버전이_바뀌면_다시_추출한다() {
        Policy policy = policy("월세 지원", "월 최대 20만원씩 12개월");
        policy.updateBenefit(cash(200_000L).toPolicyBenefit("policy-benefit-v0", BenefitSource.of(policy).contentHash(),
                LocalDateTime.now()));
        when(policyRepository.findAll()).thenReturn(List.of(policy));
        when(extractor.extract(any())).thenReturn(cash(200_000L));

        BenefitExtractionResult result = service.extractPending();

        assertThat(result.targetCount()).isEqualTo(1);
        assertThat(policy.getBenefit().getPromptVersion()).isEqualTo(VERSION);
    }

    @Test
    void all이면_이미_추출한_정책도_다시_추출한다() {
        Policy policy = policy("월세 지원", "월 최대 20만원씩 12개월");
        policy.updateBenefit(cash(200_000L).toPolicyBenefit(VERSION, BenefitSource.of(policy).contentHash(), LocalDateTime.now()));
        when(policyRepository.findAll()).thenReturn(List.of(policy));
        when(extractor.extract(any())).thenReturn(cash(200_000L));

        BenefitExtractionResult result = service.extractAll();

        assertThat(result.targetCount()).isEqualTo(1);
        verify(extractor).extract(any());
    }

    @Test
    void 한_건이_실패해도_나머지는_계속하고_실패한_정책은_기존_값을_유지한다() {
        Policy broken = policy("응답 오류", "월 10만원");
        Policy ok = policy("월세 지원", "월 최대 20만원씩 12개월");
        LocalDateTime before = LocalDateTime.of(2026, 1, 1, 0, 0);
        broken.updateBenefit(cash(100_000L).toPolicyBenefit(VERSION, "이전 원문 해시", before));
        when(policyRepository.findAll()).thenReturn(List.of(broken, ok));
        when(extractor.extract(BenefitSource.of(broken))).thenThrow(new BenefitExtractionException("JSON 아님"));
        when(extractor.extract(BenefitSource.of(ok))).thenReturn(cash(200_000L));

        BenefitExtractionResult result = service.extractPending();

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.savedByType()).containsEntry(BenefitType.CASH, 1);
        assertThat(broken.getBenefit().getExtractedAt()).isEqualTo(before);
        assertThat(broken.getBenefit().getMonthlyAmount()).isEqualTo(100_000L);
    }

    @Test
    void AI_호출_실패는_실패로_세고_계속한다() {
        Policy first = policy("A", "월 10만원");
        Policy second = policy("B", "월 20만원");
        when(policyRepository.findAll()).thenReturn(List.of(first, second));
        when(extractor.extract(BenefitSource.of(first))).thenThrow(new AiCallFailedException(AiClientException.Reason.SERVER_ERROR));
        when(extractor.extract(BenefitSource.of(second))).thenReturn(cash(200_000L));

        BenefitExtractionResult result = service.extractPending();

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.savedByType()).containsEntry(BenefitType.CASH, 1);
    }

    @Test
    void AI를_쓸_수_없으면_남은_정책은_시도하지_않고_건너뛴다() {
        Policy first = policy("A", "월 10만원");
        Policy second = policy("B", "월 20만원");
        Policy third = policy("C", "월 30만원");
        when(policyRepository.findAll()).thenReturn(List.of(first, second, third));
        when(extractor.extract(any())).thenThrow(new AiUnavailableException(AiClientException.Reason.QUOTA_EXCEEDED));

        BenefitExtractionResult result = service.extractPending();

        assertThat(result.skippedCount()).isEqualTo(3);
        assertThat(result.failedCount()).isZero();
        verify(extractor, times(1)).extract(any());
    }

    @Test
    void 추출하는_동안_삭제된_정책은_저장하지_않는다() {
        Policy policy = policy("월세 지원", "월 최대 20만원씩 12개월");
        when(policyRepository.findAll()).thenReturn(List.of(policy));
        when(policyRepository.findById(policy.getPolicyId())).thenReturn(Optional.empty());
        when(extractor.extract(any())).thenReturn(cash(200_000L));

        BenefitExtractionResult result = service.extractPending();

        assertThat(result.savedByType()).isEmpty();
        assertThat(result.failedCount()).isEqualTo(1);
    }

    @Test
    void 이미_실행_중이면_새_요청을_거절한다() throws Exception {
        Policy policy = policy("월세 지원", "월 최대 20만원씩 12개월");
        when(policyRepository.findAll()).thenReturn(List.of(policy));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(extractor.extract(any())).thenAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return cash(200_000L);
        });

        CompletableFuture<BenefitExtractionResult> first = CompletableFuture.supplyAsync(service::extractPending);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(service::extractAll).isInstanceOf(BenefitExtractionInProgressException.class);

        release.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS).savedByType()).containsEntry(BenefitType.CASH, 1);
    }
}
