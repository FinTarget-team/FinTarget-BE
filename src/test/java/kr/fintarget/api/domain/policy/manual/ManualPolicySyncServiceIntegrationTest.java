package kr.fintarget.api.domain.policy.manual;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import kr.fintarget.api.domain.policy.recommend.ApplyPeriodClassifier;
import kr.fintarget.api.domain.policy.recommend.ApplyPeriodStatus;
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

import java.time.LocalDate;
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
                policyChunkEmbeddingService, policyRerankPrecomputer, textCorrector, entityManager);

        ManualPolicySyncResult result = withBusyExtraction.sync();

        Map<String, Object> steps = result.steps();
        assertThat(steps.get("benefit").toString()).startsWith("skipped:");
        assertThat(steps).containsEntry("benefitOverride", 4);
        assertThat(manual(HOPE).getBenefit().getMonthlyAmount()).isEqualTo(150_000L);
        verify(policyChunkEmbeddingService).refreshPending();
    }

    // ===== 동기화 정책 혜택 보정 (#245) =====

    private Policy saveYouthCenter(String externalId, String name, String supportContent) {
        return policyRepository.save(Policy.create(name, "설명", 19, 39, null, null, PolicyType.HOUSING, "SEOUL", externalId,
                "YOUTH_CENTER", PolicyText.builder().supportContent(supportContent).build()));
    }

    @Test
    void 혜택_보정_대상_동기화_정책은_출처는_그대로_두고_혜택만_리소스_값으로_바꾸고_다른_정책은_그대로다() {
        saveYouthCenter("20261021005400214208", "2026 청년 월세 지원", "월 임차료 지원(최대 20만원/서울시 12개월, 국토부 24개월)");
        saveYouthCenter("YC-OTHER", "다른 월세 정책", "월 최대 10만원 지원");

        ManualPolicySyncResult result = service.sync();

        Policy rent = policyRepository.findByExternalIdAndSource("20261021005400214208", "YOUTH_CENTER").orElseThrow();
        assertThat(rent.getSource()).isEqualTo("YOUTH_CENTER");
        assertThat(rent.getBenefit().getType()).isEqualTo(BenefitType.CASH);
        assertThat(rent.getBenefit().getMonthlyAmount()).isEqualTo(200_000L);
        assertThat(rent.getBenefit().getBenefitMonths()).isEqualTo(12);
        assertThat(rent.hasBenefitForSimulation()).isTrue();
        Policy other = policyRepository.findByExternalIdAndSource("YC-OTHER", "YOUTH_CENTER").orElseThrow();
        assertThat(other.getBenefit().getType()).isEqualTo(BenefitType.SERVICE);

        assertThat(result.benefitOverrides()).hasSize(6);
        assertThat(result.benefitOverrides()).filteredOn(o -> o.externalId().equals("20261021005400214208"))
                .singleElement().satisfies(o -> {
                    assertThat(o.found()).isTrue();
                    assertThat(o.nameMatches()).isTrue();
                    assertThat(o.simulationReflected()).isTrue();
                });
        // 이 환경에 없는 외부 ID는 적용하지 않았다고 알린다
        assertThat(result.benefitOverrides()).filteredOn(o -> o.externalId().equals("20260710005400113256"))
                .singleElement().satisfies(o -> assertThat(o.found()).isFalse());
    }

    @Test
    void 동기화로_원문이_바뀌어_혜택을_다시_추출해도_보정_값이_유지된다() {
        saveYouthCenter("20261021005400214208", "2026 청년 월세 지원", "월 임차료 지원(최대 20만원/서울시 12개월, 국토부 24개월)");
        service.sync();

        // 03:00 동기화가 원문을 바꿔 원문 해시가 달라짐 → 다음 혜택 추출 대상
        Policy fetched = Policy.create("2026 청년 월세 지원", "설명", 19, 39, null, null, PolicyType.HOUSING, "SEOUL",
                "20261021005400214208", "YOUTH_CENTER",
                PolicyText.builder().supportContent("월 임차료 지원(최대 20만원/서울시 12개월, 국토부 24개월) 수정").build());
        transactionTemplate.executeWithoutResult(status -> policySyncWriter.apply("YOUTH_CENTER", List.of(fetched)));
        assertThat(benefitExtractionService.extractPending().targetCount()).isEqualTo(1);
        benefitExtractionService.extractAll();

        Policy rent = policyRepository.findByExternalIdAndSource("20261021005400214208", "YOUTH_CENTER").orElseThrow();
        assertThat(rent.getText().getSupportContent()).endsWith("수정");
        assertThat(rent.getBenefit().getType()).isEqualTo(BenefitType.CASH);
        assertThat(rent.getBenefit().getMonthlyAmount()).isEqualTo(200_000L);
    }

    @Test
    void 정책명이_다르면_혜택_보정을_적용하지_않는다() {
        saveYouthCenter("20261021005400214208", "이름이 바뀐 정책", "월 10만원");

        ManualPolicySyncResult result = service.sync();

        Policy policy = policyRepository.findByExternalIdAndSource("20261021005400214208", "YOUTH_CENTER").orElseThrow();
        assertThat(policy.getBenefit().getType()).isEqualTo(BenefitType.SERVICE);
        assertThat(result.benefitOverrides()).filteredOn(o -> o.externalId().equals("20261021005400214208"))
                .singleElement().satisfies(o -> {
                    assertThat(o.found()).isTrue();
                    assertThat(o.nameMatches()).isFalse();
                    assertThat(o.simulationReflected()).isFalse();
                });
    }

    // ===== 동기화 정책 원문 보정 (#245) =====

    private static final String RENT = "20261021005400214208";
    private static final String EMPLOYMENT = "20260710005400113256";
    private static final String RENT_NOTE = "2026년 신청 접수는 5월 6일~5월 19일 서울주거포털에서 온라인으로 받았고 마감됐어요.";
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    private static Policy youthCenter(String externalId, String name, String applyPeriod, String businessPeriod, String applyMethod) {
        return Policy.create(name, "설명", 19, 39, null, null, PolicyType.HOUSING, "SEOUL", externalId, "YOUTH_CENTER",
                PolicyText.builder().supportContent("월 임차료 지원").applyPeriod(applyPeriod).applyPeriodCode("0057001")
                        .businessPeriod(businessPeriod).applyMethod(applyMethod).build());
    }

    private Policy youth(String externalId) {
        return policyRepository.findByExternalIdAndSource(externalId, "YOUTH_CENTER").orElseThrow();
    }

    @Test
    void 원문_보정_대상은_신청_기간을_바꾸고_문장을_덧붙여_신청_가능_판정이_마감으로_바뀌고_다른_정책은_그대로다() {
        policyRepository.save(youthCenter(RENT, "2026 청년 월세 지원", "20260101 ~ 20261231", "20260101 ~ 20261231", "모집 공고 후 신청"));
        policyRepository.save(youthCenter("YC-OTHER", "다른 월세 정책", "20260101 ~ 20261231", null, "온라인 신청"));
        assertThat(ApplyPeriodClassifier.classify(youth(RENT).getText(), TODAY)).isEqualTo(ApplyPeriodStatus.OPEN);

        ManualPolicySyncResult result = service.sync();

        Policy rent = youth(RENT);
        assertThat(rent.getSource()).isEqualTo("YOUTH_CENTER");
        assertThat(rent.getText().getApplyPeriod()).isEqualTo("20260506 ~ 20260519");
        assertThat(rent.getText().getApplyMethod()).startsWith("모집 공고 후 신청\n").contains(RENT_NOTE);
        assertThat(rent.getText().getEtcNote()).startsWith(RENT_NOTE);
        assertThat(rent.getText().getSupportContent()).isEqualTo("월 임차료 지원");
        assertThat(rent.getText().getBusinessPeriod()).isEqualTo("20260101 ~ 20261231");
        assertThat(rent.getText().getApplyPeriodCode()).isEqualTo("0057001");
        assertThat(ApplyPeriodClassifier.classify(rent.getText(), TODAY)).isEqualTo(ApplyPeriodStatus.NOT_OPEN);
        assertThat(result.steps()).containsEntry("textOverride", 1);

        Policy other = youth("YC-OTHER");
        assertThat(other.getText().getApplyPeriod()).isEqualTo("20260101 ~ 20261231");
        assertThat(other.getText().getApplyMethod()).isEqualTo("온라인 신청");
        assertThat(other.getText().getEtcNote()).isNull();

        assertThat(result.textOverrides()).filteredOn(o -> o.externalId().equals(RENT)).singleElement().satisfies(o -> {
            assertThat(o.found()).isTrue();
            assertThat(o.nameMatches()).isTrue();
            assertThat(o.applied()).isTrue();
            assertThat(o.applyPeriod()).isEqualTo("20260506 ~ 20260519");
        });
        // 이 환경에 없는 외부 ID는 적용하지 않았다고 알린다
        assertThat(result.textOverrides()).filteredOn(o -> o.externalId().equals(EMPLOYMENT)).singleElement()
                .satisfies(o -> assertThat(o.found()).isFalse());

        // 다시 실행해도 문장을 두 번 붙이지 않는다
        ManualPolicySyncResult again = service.sync();
        assertThat(again.steps()).containsEntry("textOverride", 0);
        assertThat(youth(RENT).getText().getApplyMethod()).isEqualTo(rent.getText().getApplyMethod());
    }

    @Test
    void 동기화가_원문을_되돌려도_원문_보정을_다시_적용한다() {
        policyRepository.save(youthCenter(RENT, "2026 청년 월세 지원", "20260101 ~ 20261231", "20260101 ~ 20261231", "모집 공고 후 신청"));
        service.sync();
        String correctedMethod = youth(RENT).getText().getApplyMethod();

        // 03:00 동기화(updateFrom)가 소스 원문으로 되돌림
        Policy fetched = youthCenter(RENT, "2026 청년 월세 지원", "20260101 ~ 20261231", "20260101 ~ 20261231", "모집 공고 후 신청");
        transactionTemplate.executeWithoutResult(status -> policySyncWriter.apply("YOUTH_CENTER", List.of(fetched)));
        assertThat(youth(RENT).getText().getApplyPeriod()).isEqualTo("20260101 ~ 20261231");

        // 동기화 직후 단계(PolicySyncOrchestrator)에서 다시 적용
        assertThat(textCorrector.apply()).isEqualTo(1);
        assertThat(youth(RENT).getText().getApplyPeriod()).isEqualTo("20260506 ~ 20260519");
        assertThat(youth(RENT).getText().getApplyMethod()).isEqualTo(correctedMethod);
        assertThat(textCorrector.apply()).isZero();
    }

    @Test
    void 사업_기간이_상시인_국민취업지원제도는_신청_기간을_상시로_보정한다() {
        policyRepository.save(Policy.create("국민취업지원제도", "설명", 15, 69, null, null, PolicyType.HOUSING, null, EMPLOYMENT,
                "YOUTH_CENTER", PolicyText.builder().applyPeriodCode("0057002").businessPeriod("상시").applyMethod("고용센터 방문").build()));

        service.sync();

        Policy saved = youth(EMPLOYMENT);
        assertThat(saved.getText().getApplyPeriod()).isEqualTo("상시");
        assertThat(saved.getText().getApplyMethod()).isEqualTo("고용센터 방문");
        assertThat(ApplyPeriodClassifier.classify(saved.getText(), TODAY)).isEqualTo(ApplyPeriodStatus.OPEN);
    }

    @Test
    void 정책명이_다르면_원문_보정을_적용하지_않는다() {
        policyRepository.save(youthCenter(RENT, "이름이 바뀐 정책", "20260101 ~ 20261231", null, "온라인 신청"));

        ManualPolicySyncResult result = service.sync();

        assertThat(youth(RENT).getText().getApplyPeriod()).isEqualTo("20260101 ~ 20261231");
        assertThat(youth(RENT).getText().getApplyMethod()).isEqualTo("온라인 신청");
        assertThat(result.textOverrides()).filteredOn(o -> o.externalId().equals(RENT)).singleElement().satisfies(o -> {
            assertThat(o.found()).isTrue();
            assertThat(o.nameMatches()).isFalse();
            assertThat(o.applied()).isFalse();
        });
    }

    @Autowired
    private ManualPolicyTextCorrector textCorrector;

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
                                                        ManualPolicyTextCorrector textCorrector,
                                                        EntityManager entityManager) {
            return new ManualPolicySyncService(catalog, policyRepository, policyChunkRepository, transactionTemplate,
                    policyChunkService, policyBenefitExtractionService, policyConditionExtractionService,
                    policyChunkEmbeddingService, policyRerankPrecomputer, textCorrector, entityManager);
        }

        @Bean
        ManualPolicyTextCorrector manualPolicyTextCorrector(ManualPolicyCatalog catalog, PolicyRepository policyRepository,
                                                            TransactionTemplate transactionTemplate, EntityManager entityManager) {
            return new ManualPolicyTextCorrector(catalog, policyRepository, transactionTemplate, entityManager);
        }
    }
}
