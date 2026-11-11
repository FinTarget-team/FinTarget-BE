package kr.fintarget.api.domain.policy.benefit;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실제 JPA 트랜잭션으로 추출 결과가 커밋되는지 확인한다 (#162).
 * 단위 테스트는 가짜 트랜잭션 매니저라, 작업 스레드에서 짧은 트랜잭션으로 저장한 값이 실제로 DB에 남는지는 여기서만 검증된다.
 * 작업 스레드는 테스트 트랜잭션을 볼 수 없으므로 테스트 트랜잭션을 끄고(NOT_SUPPORTED) 각 호출이 바로 커밋되게 한다.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(PolicyBenefitExtractionServiceIntegrationTest.Config.class)
class PolicyBenefitExtractionServiceIntegrationTest {

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PolicyBenefitExtractionService service;

    @MockitoBean
    private PolicyBenefitExtractor extractor;

    @BeforeEach
    void setUp() {
        when(extractor.promptVersion()).thenReturn("policy-benefit-v1");
    }

    @AfterEach
    void tearDown() {
        policyRepository.deleteAll();
    }

    private Policy save(String externalId, String supportContent) {
        return policyRepository.save(Policy.create("정책 " + externalId, null, null, null, null, null, PolicyType.HOUSING,
                null, externalId, "YOUTH_CENTER", PolicyText.builder().supportContent(supportContent).build()));
    }

    @Test
    void 추출_결과가_DB에_커밋되고_두_번째_실행은_아무것도_다시_추출하지_않는다() {
        Policy rent = save("INT-1", "월 최대 20만원씩 12개월 지원");
        Policy loan = save("INT-2", "대출이자 최대 300만원 지원");
        when(extractor.extract(any())).thenAnswer(invocation -> {
            BenefitSource source = invocation.getArgument(0);
            return source.supportContent().contains("대출")
                    ? BenefitExtraction.of(BenefitType.LOAN, "대출이자 최대 300만원 지원", null)
                    : new BenefitExtraction(BenefitType.CASH, 200_000L, 12, null, true, "월 최대 20만원씩 12개월 지원", null);
        });

        BenefitExtractionResult first = service.extractPending();

        assertThat(first.targetCount()).isEqualTo(2);
        assertThat(first.savedByType()).containsEntry(BenefitType.CASH, 1).containsEntry(BenefitType.LOAN, 1);
        Policy storedRent = policyRepository.findById(rent.getPolicyId()).orElseThrow();
        Policy storedLoan = policyRepository.findById(loan.getPolicyId()).orElseThrow();
        assertThat(storedRent.hasBenefitForSimulation()).isTrue();
        assertThat(storedRent.getBenefit().getMonthlyAmount()).isEqualTo(200_000L);
        assertThat(storedLoan.getBenefit().getType()).isEqualTo(BenefitType.LOAN);
        assertThat(storedLoan.hasBenefitForSimulation()).isFalse();

        BenefitExtractionResult second = service.extractPending();

        assertThat(second.targetCount()).isZero();
        verify(extractor, times(2)).extract(any());
    }

    @Test
    void 원문이_바뀐_정책만_다시_추출한다() {
        Policy changed = save("INT-3", "월 최대 20만원씩 12개월 지원");
        save("INT-4", "월 10만원 지원");
        when(extractor.extract(any())).thenReturn(
                new BenefitExtraction(BenefitType.CASH, 100_000L, null, null, false, "근거", null));
        service.extractPending();

        // 동기화가 원문을 바꾼 상황
        Policy stored = policyRepository.findById(changed.getPolicyId()).orElseThrow();
        stored.updateFrom(Policy.create(stored.getName(), null, null, null, null, null, PolicyType.HOUSING, null,
                "INT-3", "YOUTH_CENTER", PolicyText.builder().supportContent("월 최대 30만원씩 12개월 지원").build()));
        policyRepository.save(stored);

        BenefitExtractionResult result = service.extractPending();

        assertThat(result.targetCount()).isEqualTo(1);
    }

    @Test
    void 추출에_실패한_정책은_저장하지_않고_다음_실행에서_다시_시도한다() {
        Policy policy = save("INT-5", "월 10만원 지원");
        when(extractor.extract(any())).thenThrow(new BenefitExtractionException("JSON 아님"));

        BenefitExtractionResult failed = service.extractPending();

        assertThat(failed.failedCount()).isEqualTo(1);
        assertThat(policyRepository.findById(policy.getPolicyId()).orElseThrow().getBenefit()).isNull();
        assertThat(service.extractPending().targetCount()).isEqualTo(1);
    }

    static class Config {
        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        @Bean
        PolicyBenefitExtractionService policyBenefitExtractionService(PolicyRepository policyRepository,
                                                                      PolicyBenefitExtractor extractor,
                                                                      TransactionTemplate transactionTemplate) {
            return new PolicyBenefitExtractionService(policyRepository, extractor, transactionTemplate, 2);
        }
    }
}
