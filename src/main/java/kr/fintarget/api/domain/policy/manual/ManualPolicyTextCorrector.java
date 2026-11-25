package kr.fintarget.api.domain.policy.manual;

import jakarta.persistence.EntityManager;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.manual.ManualPolicyCatalog.TextOverride;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.Optional;

/**
 * 리소스의 원문 보정(textOverrides)을 동기화 정책에 적용한다 (#245).
 *
 * <p>정책 동기화(Policy.updateFrom)는 원문을 소스 값으로 되돌리므로, 동기화 직후(PolicySyncOrchestrator, 청크·추출 전)와
 * 직접 등록 정책 동기화(ManualPolicySyncService, 청크·추출 전)에서 다시 적용한다. 동기화 로직 자체는 바꾸지 않는다.
 * 청크·혜택·조건은 보정한 원문으로 해시를 만들므로, 동기화가 되돌렸다가 다시 보정하면 해시가 같아 AI를 다시 부르지 않는다.
 * 출처·외부 정책 ID·정책명이 모두 같을 때만 적용하고, 이미 보정된 원문이면 바꾸지 않는다.
 *
 * <p>open-in-view 요청 스레드에서도 옛 엔티티를 덮어쓰지 않도록 읽기 전에 영속성 컨텍스트를 비운다.
 */
@Slf4j
@Component
public class ManualPolicyTextCorrector {

    private final ManualPolicyCatalog catalog;
    private final PolicyRepository policyRepository;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    public ManualPolicyTextCorrector(ManualPolicyCatalog catalog, PolicyRepository policyRepository,
                                     TransactionTemplate transactionTemplate, EntityManager entityManager) {
        this.catalog = catalog;
        this.policyRepository = policyRepository;
        this.transactionTemplate = transactionTemplate;
        this.entityManager = entityManager;
    }

    /** 원문 보정을 적용한다. 원문을 바꾼 정책 수 */
    public int apply() {
        if (catalog.textOverrides().isEmpty()) {
            return 0;
        }
        int changed = Objects.requireNonNull(transactionTemplate.execute(status -> applyInTransaction()));
        if (changed > 0) {
            log.info("동기화 정책 원문 보정 적용 {}건", changed);
        }
        return changed;
    }

    private int applyInTransaction() {
        entityManager.clear();
        int changed = 0;
        for (TextOverride override : catalog.textOverrides()) {
            Optional<Policy> found = policyRepository.findByExternalIdAndSource(override.externalId(), override.source());
            if (found.isEmpty()) {
                continue;
            }
            Policy policy = found.get();
            if (!override.matches(policy)) {
                log.warn("원문 보정 대상의 정책명이 달라 적용하지 않음 externalId={} 리소스={} 저장={}",
                        override.externalId(), override.name(), policy.getName());
                continue;
            }
            if (!override.isApplied(policy.getText())) {
                policy.correctText(override.correct(policy.getText()));
                changed++;
            }
        }
        return changed;
    }
}
