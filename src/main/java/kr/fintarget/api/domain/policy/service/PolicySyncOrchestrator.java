package kr.fintarget.api.domain.policy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.IntSupplier;

/**
 * 온통청년/KINFA 정책 동기화를 순차 실행한다. 스케줄러와 수동 트리거 API가 공유한다.
 * 한쪽 API 장애가 다른 쪽 동기화를 막지 않도록 각각 격리해서 실행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicySyncOrchestrator {

    private static final int PAGE_INDEX = 1;
    private static final int PAGE_SIZE = 100;

    private final YouthCenterPolicySyncService youthCenterPolicySyncService;
    private final KinfaPolicySyncService kinfaPolicySyncService;

    public PolicySyncResult syncAll() {
        long start = System.currentTimeMillis();

        SourceResult youthCenterResult = runSync("YOUTH_CENTER",
                () -> youthCenterPolicySyncService.sync(PAGE_INDEX, PAGE_SIZE));
        SourceResult kinfaResult = runSync("KINFA",
                () -> kinfaPolicySyncService.sync(PAGE_INDEX, PAGE_SIZE));

        long elapsedMs = System.currentTimeMillis() - start;
        int failedSourceCount = (youthCenterResult.failed() ? 1 : 0) + (kinfaResult.failed() ? 1 : 0);

        log.info("정책 동기화 완료 - 총 신규 저장 {}건, 실패한 소스 {}건, 총 소요시간 {}ms",
                youthCenterResult.savedCount() + kinfaResult.savedCount(), failedSourceCount, elapsedMs);

        return new PolicySyncResult(
                youthCenterResult.savedCount(), youthCenterResult.failed(),
                kinfaResult.savedCount(), kinfaResult.failed(),
                elapsedMs
        );
    }

    private SourceResult runSync(String source, IntSupplier syncCall) {
        long start = System.currentTimeMillis();
        try {
            int savedCount = syncCall.getAsInt();
            long elapsedMs = System.currentTimeMillis() - start;
            log.info("[{}] 정책 동기화 성공 - 신규 저장 {}건, 소요시간 {}ms", source, savedCount, elapsedMs);
            return new SourceResult(savedCount, false);
        } catch (Exception e) {
            long elapsedMs = System.currentTimeMillis() - start;
            log.error("[{}] 정책 동기화 실패 - 소요시간 {}ms", source, elapsedMs, e);
            return new SourceResult(0, true);
        }
    }

    private record SourceResult(int savedCount, boolean failed) {
    }
}
