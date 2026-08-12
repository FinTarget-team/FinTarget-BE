package kr.fintarget.api.domain.policy.scheduler;

import kr.fintarget.api.domain.policy.service.PolicySyncOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 새벽 3시, 온통청년/KINFA 정책 데이터를 동기화한다.
 * policy.sync.scheduler.enabled=false 이면 등록되지 않는다 (로컬 개발 기본값).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "policy.sync.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PolicySyncScheduler {

    private final PolicySyncOrchestrator policySyncOrchestrator;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void syncPolicies() {
        policySyncOrchestrator.syncAll();
    }
}
