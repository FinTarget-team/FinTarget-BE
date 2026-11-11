package kr.fintarget.api.domain.policy.scheduler;

import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.policy.service.PolicySyncInProgressException;
import kr.fintarget.api.domain.policy.service.PolicySyncOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 앱 기동 시 정책 테이블이 비어 있으면 즉시 1회 동기화한다.
 * 새로 배포하거나 DB를 초기화한 직후 새벽 3시 스케줄러를 기다리지 않도록 하기 위함이다.
 * 스케줄러와 같은 policy.sync.scheduler.enabled 설정을 따른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "policy.sync.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PolicyInitialSyncRunner {

    private final PolicyRepository policyRepository;
    private final PolicySyncOrchestrator policySyncOrchestrator;

    @EventListener(ApplicationReadyEvent.class)
    public void syncIfEmpty() {
        long policyCount = policyRepository.count();
        if (policyCount > 0) {
            log.info("정책 데이터 {}건 존재 - 기동 시 동기화 생략", policyCount);
            return;
        }
        log.info("정책 데이터가 비어 있어 기동 시 동기화를 실행합니다");
        try {
            policySyncOrchestrator.syncAll();
        } catch (PolicySyncInProgressException e) {
            log.warn("정책 동기화가 이미 실행 중이어서 기동 시 동기화를 건너뜁니다");
        }
    }
}
