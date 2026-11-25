package kr.fintarget.api.domain.policy.manual;

import jakarta.persistence.EntityManager;
import kr.fintarget.api.domain.policy.benefit.PolicyBenefitExtractionService;
import kr.fintarget.api.domain.policy.chunk.PolicyChunkRepository;
import kr.fintarget.api.domain.policy.chunk.PolicyChunkService;
import kr.fintarget.api.domain.policy.condition.PolicyConditionExtractionService;
import kr.fintarget.api.domain.policy.embedding.PolicyChunkEmbeddingService;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyBenefit;
import kr.fintarget.api.domain.policy.entity.PolicyCondition;
import kr.fintarget.api.domain.policy.manual.ManualPolicyCatalog.BenefitOverride;
import kr.fintarget.api.domain.policy.manual.ManualPolicyCatalog.ManualPolicy;
import kr.fintarget.api.domain.policy.manual.ManualPolicyCatalog.Pin;
import kr.fintarget.api.domain.policy.manual.ManualPolicyCatalog.TextOverride;
import kr.fintarget.api.domain.policy.manual.ManualPolicySyncResult.Item;
import kr.fintarget.api.domain.policy.manual.ManualPolicySyncResult.OverrideItem;
import kr.fintarget.api.domain.policy.manual.ManualPolicySyncResult.PinItem;
import kr.fintarget.api.domain.policy.manual.ManualPolicySyncResult.TextOverrideItem;
import kr.fintarget.api.domain.policy.recommend.rerank.PolicyRerankPrecomputer;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 직접 등록 정책 동기화 (#245). 리소스의 정책을 externalId로 upsert한 뒤, 정책 동기화(PolicySyncOrchestrator)와 같은 순서로
 * 청크 → 혜택 추출 → 조건 추출 → (리소스 혜택 값 적용) → 리랭킹 미리 계산 → 임베딩을 실행한다. 여러 번 실행해도 결과가 같다.
 * 리소스 혜택 값 적용 단계는 동기화 정책 혜택 보정(benefitOverrides)도 함께 적용한다 (출처는 그대로, 혜택 값만).
 * 동기화 정책 원문 보정(textOverrides)은 upsert 직후, 청크보다 먼저 적용한다 ({@link ManualPolicyTextCorrector}).
 *
 * <p>출처를 {@link ManualPolicyCatalog#SOURCE}로 저장하므로 03:00 동기화(온통청년·KINFA)는 이 정책을 지우거나 덮어쓰지 않는다
 * (PolicySyncWriter는 같은 출처 정책만 갱신·삭제).
 * 각 단계는 "아직 처리하지 않았거나 원문이 바뀐 정책"만 처리하는 기존 메서드를 그대로 부르므로, 바뀐 것이 없으면 AI를 호출하지 않는다.
 * 다른 작업(03:00 동기화, 관리자 백필)과 겹쳐 한 단계가 이미 실행 중이면 그 단계는 건너뛰고 결과에 사유를 남긴다.
 * 앱 시작 시 자동 실행하지 않는다 (관리자 API로 호출).
 *
 * <p>open-in-view가 켜져 있으면 요청 스레드의 영속성 컨텍스트가 요청 끝까지 남는다. 추출 단계는 다른 스레드에서 저장하므로,
 * 추출 뒤 요청 스레드에서 정책을 다시 읽기 전에 영속성 컨텍스트를 비운다. 비우지 않으면 등록 때 읽은 엔티티(조건·혜택 없음)를
 * 그대로 다시 저장해 추출 결과를 덮어쓴다.
 */
@Slf4j
@Service
public class ManualPolicySyncService {

    private final ManualPolicyCatalog catalog;
    private final PolicyRepository policyRepository;
    private final PolicyChunkRepository policyChunkRepository;
    private final TransactionTemplate transactionTemplate;
    private final PolicyChunkService policyChunkService;
    private final PolicyBenefitExtractionService policyBenefitExtractionService;
    private final PolicyConditionExtractionService policyConditionExtractionService;
    private final PolicyChunkEmbeddingService policyChunkEmbeddingService;
    private final PolicyRerankPrecomputer policyRerankPrecomputer;
    private final ManualPolicyTextCorrector textCorrector;
    private final EntityManager entityManager;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ManualPolicySyncService(ManualPolicyCatalog catalog, PolicyRepository policyRepository,
                                   PolicyChunkRepository policyChunkRepository, TransactionTemplate transactionTemplate,
                                   PolicyChunkService policyChunkService,
                                   PolicyBenefitExtractionService policyBenefitExtractionService,
                                   PolicyConditionExtractionService policyConditionExtractionService,
                                   PolicyChunkEmbeddingService policyChunkEmbeddingService,
                                   PolicyRerankPrecomputer policyRerankPrecomputer, ManualPolicyTextCorrector textCorrector,
                                   EntityManager entityManager) {
        this.catalog = catalog;
        this.policyRepository = policyRepository;
        this.policyChunkRepository = policyChunkRepository;
        this.transactionTemplate = transactionTemplate;
        this.policyChunkService = policyChunkService;
        this.policyBenefitExtractionService = policyBenefitExtractionService;
        this.policyConditionExtractionService = policyConditionExtractionService;
        this.policyChunkEmbeddingService = policyChunkEmbeddingService;
        this.policyRerankPrecomputer = policyRerankPrecomputer;
        this.textCorrector = textCorrector;
        this.entityManager = entityManager;
    }

    /** @throws ManualPolicySyncInProgressException 이미 실행 중일 때 (409) */
    public ManualPolicySyncResult sync() {
        if (!running.compareAndSet(false, true)) {
            throw new ManualPolicySyncInProgressException();
        }
        try {
            return doSync();
        } finally {
            running.set(false);
        }
    }

    private ManualPolicySyncResult doSync() {
        long start = System.currentTimeMillis();
        Map<String, String> statuses = Objects.requireNonNull(transactionTemplate.execute(status -> upsertAll()));

        Map<String, Object> steps = new LinkedHashMap<>();
        // 동기화 정책 원문 보정은 청크·추출 전에 적용해, 보정한 원문으로 청크·임베딩·조건·혜택을 다시 만든다
        steps.put("textOverride", step(textCorrector::apply));
        steps.put("chunk", step(policyChunkService::refreshPending));
        steps.put("benefit", step(policyBenefitExtractionService::extractPending));
        steps.put("condition", step(policyConditionExtractionService::extractPending));
        // 혜택 추출이 건너뛰어졌거나(실행 중·AI 없음) 원문은 그대로인데 리소스 혜택 값만 바뀐 경우에도 리소스 값을 맞춘다
        steps.put("benefitOverride", step(() -> transactionTemplate.execute(status -> applyResourceBenefits())));
        steps.put("rerank", step(() -> policyRerankPrecomputer.requestAll() ? "requested" : "not started (리랭킹 꺼짐 또는 AI 키 없음)"));
        steps.put("embedding", step(policyChunkEmbeddingService::refreshPending));

        List<Item> items = Objects.requireNonNull(transactionTemplate.execute(status -> items(statuses)));
        List<OverrideItem> overrides = Objects.requireNonNull(transactionTemplate.execute(status -> overrideItems()));
        List<TextOverrideItem> textOverrides = Objects.requireNonNull(transactionTemplate.execute(status -> textOverrideItems()));
        List<PinItem> pins = Objects.requireNonNull(transactionTemplate.execute(status -> pinItems()));
        long elapsedMs = System.currentTimeMillis() - start;
        log.info("직접 등록 정책 동기화 완료 정책 {}건 {} 혜택 보정 {}건 소요 {}ms", items.size(), statuses, overrides.size(), elapsedMs);
        return new ManualPolicySyncResult(items, overrides, textOverrides, pins, steps, elapsedMs);
    }

    // externalId → CREATED / UPDATED
    private Map<String, String> upsertAll() {
        Map<String, String> statuses = new LinkedHashMap<>();
        for (ManualPolicy manual : catalog.policies()) {
            Policy fresh = manual.toPolicy();
            Optional<Policy> existing = policyRepository.findByExternalIdAndSource(manual.externalId(), ManualPolicyCatalog.SOURCE);
            if (existing.isPresent()) {
                existing.get().updateFrom(fresh);
                statuses.put(manual.externalId(), "UPDATED");
            } else {
                policyRepository.save(fresh);
                statuses.put(manual.externalId(), "CREATED");
            }
        }
        return statuses;
    }

    /** 리소스 혜택 값과 다른 직접 등록 정책·혜택 보정 대상만 고친다. 고친 건수 */
    int applyResourceBenefits() {
        entityManager.clear();
        List<Policy> targets = new ArrayList<>(policyRepository.findBySource(ManualPolicyCatalog.SOURCE));
        for (BenefitOverride override : catalog.benefitOverrides()) {
            policyRepository.findByExternalIdAndSource(override.externalId(), override.source()).ifPresent(targets::add);
        }
        int changed = 0;
        for (Policy policy : targets) {
            PolicyBenefit current = policy.getBenefit();
            PolicyBenefit resource = catalog.applyBenefit(policy, current);
            if (resource != current && !sameValues(current, resource)) {
                policy.updateBenefit(resource);
                changed++;
            }
        }
        return changed;
    }

    private static boolean sameValues(PolicyBenefit a, PolicyBenefit b) {
        return a != null && b != null && a.getType() == b.getType()
                && Objects.equals(a.getMonthlyAmount(), b.getMonthlyAmount())
                && Objects.equals(a.getBenefitMonths(), b.getBenefitMonths())
                && Objects.equals(a.getOneTimeAmount(), b.getOneTimeAmount())
                && Objects.equals(a.getMaximum(), b.getMaximum())
                && Objects.equals(a.getEvidence(), b.getEvidence());
    }

    private List<Item> items(Map<String, String> statuses) {
        entityManager.clear();
        List<Item> items = new ArrayList<>();
        for (ManualPolicy manual : catalog.policies()) {
            policyRepository.findByExternalIdAndSource(manual.externalId(), ManualPolicyCatalog.SOURCE).ifPresent(policy -> {
                PolicyCondition condition = policy.getCondition();
                PolicyBenefit benefit = policy.getBenefit();
                items.add(new Item(policy.getPolicyId(), manual.externalId(), policy.getName(), statuses.get(manual.externalId()),
                        policyChunkRepository.findByPolicyIdOrderBySeqAsc(policy.getPolicyId()).size(),
                        policy.getMinAge(), policy.getMaxAge(), policy.matchingRegionCodes(),
                        condition == null ? null : String.valueOf(condition.getIncomeBasis()),
                        condition == null ? null : condition.getIncomeMaxMonthly(),
                        condition == null ? null : condition.isSpecialTarget(),
                        condition == null ? null : condition.getSpecialTargetGroup(),
                        condition == null ? null : String.valueOf(condition.getBenefitKind()),
                        benefit == null ? null : String.valueOf(benefit.getType()),
                        benefit == null ? null : benefit.getMonthlyAmount(),
                        benefit == null ? null : benefit.getBenefitMonths(),
                        benefit == null ? null : benefit.getOneTimeAmount(),
                        policy.hasBenefitForSimulation()));
            });
        }
        return items;
    }

    // 혜택 보정 대상별 결과. 대상 정책이 없거나(외부 ID가 다른 환경) 정책명이 다르면 적용하지 않은 것으로 알린다
    private List<OverrideItem> overrideItems() {
        entityManager.clear();
        List<OverrideItem> items = new ArrayList<>();
        for (BenefitOverride override : catalog.benefitOverrides()) {
            Optional<Policy> found = policyRepository.findByExternalIdAndSource(override.externalId(), override.source());
            Policy policy = found.orElse(null);
            PolicyBenefit benefit = policy == null ? null : policy.getBenefit();
            items.add(new OverrideItem(override.source(), override.externalId(), override.name(), found.isPresent(),
                    policy != null && override.name().equals(policy.getName()),
                    benefit == null ? null : String.valueOf(benefit.getType()),
                    benefit == null ? null : benefit.getMonthlyAmount(),
                    benefit == null ? null : benefit.getBenefitMonths(),
                    benefit == null ? null : benefit.getOneTimeAmount(),
                    policy != null && policy.hasBenefitForSimulation()));
        }
        return items;
    }

    // 원문 보정 대상별 결과. 대상 정책이 없거나(외부 ID가 다른 환경) 정책명이 다르면 적용하지 않은 것으로 알린다
    private List<TextOverrideItem> textOverrideItems() {
        entityManager.clear();
        List<TextOverrideItem> items = new ArrayList<>();
        for (TextOverride override : catalog.textOverrides()) {
            Policy policy = policyRepository.findByExternalIdAndSource(override.externalId(), override.source()).orElse(null);
            boolean matches = override.matches(policy);
            items.add(new TextOverrideItem(override.source(), override.externalId(), override.name(), policy != null, matches,
                    matches && override.isApplied(policy.getText()),
                    policy == null || policy.getText() == null ? null : policy.getText().getApplyPeriod()));
        }
        return items;
    }

    // 고정 노출 대상별 결과. 대상 정책이 없거나(외부 ID가 다른 환경) 정책명이 다르면 추천 목록에 고정하지 않는다
    private List<PinItem> pinItems() {
        entityManager.clear();
        List<PinItem> items = new ArrayList<>();
        for (Pin pin : catalog.pins()) {
            Policy policy = policyRepository.findByExternalIdAndSource(pin.externalId(), pin.source()).orElse(null);
            items.add(new PinItem(pin.source(), pin.externalId(), pin.name(), pin.recommendFor(), policy != null, pin.matches(policy)));
        }
        return items;
    }

    // 한 단계가 실패하거나 이미 실행 중이어도 다음 단계를 계속한다. 결과 또는 사유를 응답에 남긴다
    private Object step(Supplier<?> action) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            log.warn("직접 등록 정책 동기화 단계 건너뜀: {}", e.getMessage());
            return "skipped: " + e.getMessage();
        }
    }
}
