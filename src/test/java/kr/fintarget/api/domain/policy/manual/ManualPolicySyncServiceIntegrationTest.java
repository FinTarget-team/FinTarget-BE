package kr.fintarget.api.domain.policy.manual;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import kr.fintarget.api.domain.policy.benefit.BenefitExtraction;
import kr.fintarget.api.domain.policy.benefit.BenefitExtractionInProgressException;
import kr.fintarget.api.domain.policy.benefit.BenefitType;
import kr.fintarget.api.domain.policy.benefit.PolicyBenefitExtractionService;
import kr.fintarget.api.domain.policy.benefit.PolicyBenefitExtractor;
import kr.fintarget.api.domain.policy.chunk.PolicyChunkRepository;
import kr.fintarget.api.domain.policy.chunk.PolicyChunkService;
import kr.fintarget.api.domain.policy.condition.PolicyConditionExtractionService;
import kr.fintarget.api.domain.policy.embedding.PolicyChunkEmbeddingService;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.recommend.rerank.PolicyRerankPrecomputer;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.policy.repository.UserPolicyRepository;
import kr.fintarget.api.domain.policy.service.PolicySyncWriter;
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
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 직접 등록 정책 동기화(#245)를 실제 JPA 트랜잭션으로 검증한다: 최초 등록, 재실행 시 중복 없음,
 * 온통청년·KINFA 동기화와 전체 혜택 재추출 뒤에도 정책과 리소스 혜택 값이 남는지.
 * 청크·조건·임베딩·리랭킹은 각자 테스트가 있어 여기서는 호출만 확인한다.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ManualPolicySyncServiceIntegrationTest.Config.class)
class ManualPolicySyncServiceIntegrationTest {

    private static final String HOPE = "MANUAL-HOPE-DOUBLE-2026";
    private static final String SCHOLARSHIP = "MANUAL-NATIONAL-SCHOLARSHIP-1-2026";

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private ManualPolicySyncService service;

    @Autowired
    private PolicyBenefitExtractionService benefitExtractionService;

    @Autowired
    private PolicySyncWriter policySyncWriter;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private PolicyBenefitExtractor extractor;

    @MockitoBean
    private PolicyChunkService policyChunkService;

    @MockitoBean
    private PolicyConditionExtractionService policyConditionExtractionService;

    @MockitoBean
    private PolicyChunkEmbeddingService policyChunkEmbeddingService;

    @MockitoBean
    private PolicyRerankPrecomputer policyRerankPrecomputer;

    @BeforeEach
    void setUp() {
        when(extractor.promptVersion()).thenReturn("policy-benefit-v1");
        // AI가 리소스와 다르게 판단한 상황 (매칭 적립을 SERVICE로 봄)
        when(extractor.extract(any())).thenReturn(BenefitExtraction.of(BenefitType.SERVICE, "AI 근거", null));
        when(policyRerankPrecomputer.requestAll()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        policyRepository.deleteAll();
    }

    private Policy manual(String externalId) {
        return policyRepository.findByExternalIdAndSource(externalId, ManualPolicyCatalog.SOURCE).orElseThrow();
    }

    @Test
    void 처음_실행하면_4개_정책을_MANUAL로_등록하고_혜택은_AI_결과_대신_리소스_값을_저장한다() {
        ManualPolicySyncResult result = service.sync();

        assertThat(result.policies()).hasSize(4)
                .allSatisfy(item -> assertThat(item.status()).isEqualTo("CREATED"));
        assertThat(policyRepository.findBySource(ManualPolicyCatalog.SOURCE)).hasSize(4);

        Policy hope = manual(HOPE);
        assertThat(hope.getName()).isEqualTo("2026 희망두배 청년통장");
        assertThat(hope.getRegion()).isEqualTo("SEOUL");
        assertThat(hope.getText().getApplyPeriod()).contains("2026.6.8 ~ 2026.6.19");
        assertThat(hope.getBenefit().getType()).isEqualTo(BenefitType.CASH);
        assertThat(hope.getBenefit().getMonthlyAmount()).isEqualTo(150_000L);
        assertThat(hope.getBenefit().getBenefitMonths()).isEqualTo(36);
        assertThat(hope.getBenefit().getMaximum()).isTrue();
        // 추출 메타데이터는 남겨 다음 실행 때 다시 추출하지 않는다
        assertThat(hope.getBenefit().getPromptVersion()).isEqualTo("policy-benefit-v1");
        assertThat(hope.hasBenefitForSimulation()).isTrue();

        Policy scholarship = manual(SCHOLARSHIP);
        assertThat(scholarship.getBenefit().getOneTimeAmount()).isEqualTo(1_000_000L);
        assertThat(scholarship.getBenefit().getMonthlyAmount()).isNull();
        assertThat(scholarship.getRegion()).isNull();

        assertThat(result.policies()).allSatisfy(item -> assertThat(item.simulationReflected()).isTrue());
        verify(policyChunkService).refreshPending();
        verify(policyConditionExtractionService).extractPending();
        verify(policyChunkEmbeddingService).refreshPending();
        verify(policyRerankPrecomputer).requestAll();
    }

    @Test
    void open_in_view처럼_요청_스레드에_영속성_컨텍스트가_남아_있어도_추출_결과를_덮어쓰지_않는다() {
        // 웹 요청처럼 이 스레드에 EntityManager를 묶어 둔다 (OpenEntityManagerInViewInterceptor와 같은 방식)
        EntityManager requestScoped = entityManagerFactory.createEntityManager();
        TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(requestScoped));
        try {
            service.sync();
        } finally {
            TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            requestScoped.close();
        }

        Policy hope = manual(HOPE);
        assertThat(hope.getBenefit().getMonthlyAmount()).isEqualTo(150_000L);
        // 다른 스레드에서 저장한 추출 메타데이터가 남아 있다 (예전 엔티티로 덮어쓰면 null이 된다)
        assertThat(hope.getBenefit().getPromptVersion()).isEqualTo("policy-benefit-v1");
        assertThat(hope.getBenefit().getSourceHash()).isNotNull();
    }

    @Test
    void 다시_실행해도_중복_없이_갱신되고_원문이_같으면_AI를_다시_호출하지_않는다() {
        service.sync();
        ManualPolicySyncResult second = service.sync();

        assertThat(second.policies()).allSatisfy(item -> assertThat(item.status()).isEqualTo("UPDATED"));
        assertThat(policyRepository.findBySource(ManualPolicyCatalog.SOURCE)).hasSize(4);
        assertThat(second.steps()).containsEntry("benefitOverride", 0);
        verify(extractor, times(4)).extract(any());
        assertThat(manual(HOPE).getBenefit().getMonthlyAmount()).isEqualTo(150_000L);
    }

    @Test
    void 온통청년_KINFA_동기화와_전체_혜택_재추출_뒤에도_정책과_리소스_혜택_값이_남는다() {
        service.sync();
        Policy youth = Policy.create("온통청년 정책", "설명", null, null, null, null, PolicyType.HOUSING, null, "YC-1",
                "YOUTH_CENTER", PolicyText.builder().supportContent("월 10만원").build());
        Policy kinfa = Policy.create("KINFA 상품", "설명", null, null, null, null, PolicyType.FINANCE_WELFARE, null, "K-1",
                "KINFA", PolicyText.builder().supportContent("대출").build());

        // 03:00 동기화: API 결과에 직접 등록 정책이 없어도 출처가 달라 지우지 않는다
        transactionTemplate.executeWithoutResult(status -> policySyncWriter.apply("YOUTH_CENTER", List.of(youth)));
        transactionTemplate.executeWithoutResult(status -> policySyncWriter.apply("KINFA", List.of(kinfa)));
        // 프롬프트 변경 등으로 전체 재추출해도 리소스 값을 유지한다
        benefitExtractionService.extractAll();

        assertThat(policyRepository.findBySource(ManualPolicyCatalog.SOURCE)).hasSize(4);
        Policy hope = manual(HOPE);
        assertThat(hope.getBenefit().getType()).isEqualTo(BenefitType.CASH);
        assertThat(hope.getBenefit().getMonthlyAmount()).isEqualTo(150_000L);
        // 다른 출처 정책은 AI 결과 그대로 (덮기는 MANUAL만)
        Policy storedYouth = policyRepository.findByExternalIdAndSource("YC-1", "YOUTH_CENTER").orElseThrow();
        assertThat(storedYouth.getBenefit().getType()).isEqualTo(BenefitType.SERVICE);
    }

    @Test
    void 혜택_추출이_이미_실행_중이어도_리소스_혜택_값을_적용하고_다음_단계를_계속한다() {
        PolicyBenefitExtractionService busy = org.mockito.Mockito.mock(PolicyBenefitExtractionService.class);
        when(busy.extractPending()).thenThrow(new BenefitExtractionInProgressException());
        ManualPolicySyncService withBusyExtraction = new ManualPolicySyncService(new ManualPolicyCatalog(), policyRepository,
                chunkRepository, transactionTemplate, policyChunkService, busy, policyConditionExtractionService,
                policyChunkEmbeddingService, policyRerankPrecomputer, entityManager);

        ManualPolicySyncResult result = withBusyExtraction.sync();

        Map<String, Object> steps = result.steps();
        assertThat(steps.get("benefit").toString()).startsWith("skipped:");
        assertThat(steps).containsEntry("benefitOverride", 4);
        assertThat(manual(HOPE).getBenefit().getMonthlyAmount()).isEqualTo(150_000L);
        verify(policyChunkEmbeddingService).refreshPending();
    }

    @Autowired
    private PolicyChunkRepository chunkRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    static class Config {
        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        @Bean
        ManualPolicyCatalog manualPolicyCatalog() {
            return new ManualPolicyCatalog();
        }

        @Bean
        PolicyBenefitExtractionService policyBenefitExtractionService(PolicyRepository policyRepository,
                                                                      PolicyBenefitExtractor extractor,
                                                                      TransactionTemplate transactionTemplate,
                                                                      ManualPolicyCatalog catalog) {
            return new PolicyBenefitExtractionService(policyRepository, extractor, transactionTemplate, 2, catalog);
        }

        @Bean
        PolicySyncWriter policySyncWriter(PolicyRepository policyRepository, UserPolicyRepository userPolicyRepository,
                                          PolicyChunkRepository policyChunkRepository) {
            return new PolicySyncWriter(policyRepository, userPolicyRepository, policyChunkRepository);
        }

        @Bean
        ManualPolicySyncService manualPolicySyncService(ManualPolicyCatalog catalog, PolicyRepository policyRepository,
                                                        PolicyChunkRepository policyChunkRepository,
                                                        TransactionTemplate transactionTemplate,
                                                        PolicyChunkService policyChunkService,
                                                        PolicyBenefitExtractionService policyBenefitExtractionService,
                                                        PolicyConditionExtractionService policyConditionExtractionService,
                                                        PolicyChunkEmbeddingService policyChunkEmbeddingService,
                                                        PolicyRerankPrecomputer policyRerankPrecomputer,
                                                        EntityManager entityManager) {
            return new ManualPolicySyncService(catalog, policyRepository, policyChunkRepository, transactionTemplate,
                    policyChunkService, policyBenefitExtractionService, policyConditionExtractionService,
                    policyChunkEmbeddingService, policyRerankPrecomputer, entityManager);
        }
    }
}
