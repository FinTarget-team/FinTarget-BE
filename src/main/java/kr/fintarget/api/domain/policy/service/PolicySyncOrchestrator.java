package kr.fintarget.api.domain.policy.service;

import kr.fintarget.api.domain.policy.benefit.BenefitExtractionInProgressException;
import kr.fintarget.api.domain.policy.benefit.PolicyBenefitExtractionService;
import kr.fintarget.api.domain.policy.chunk.PolicyChunkRefreshResult;
import kr.fintarget.api.domain.policy.chunk.PolicyChunkService;
import kr.fintarget.api.domain.policy.embedding.PolicyChunkEmbeddingService;
import kr.fintarget.api.domain.policy.embedding.PolicyEmbeddingRefreshResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

/**
 * 온통청년/KINFA 정책 동기화를 순차 실행한다. 스케줄러와 수동 트리거 API가 공유한다.
 * 한쪽 API 장애가 다른 쪽 동기화를 막지 않도록 각각 격리해서 실행한다.
 * 같은 정책을 중복 저장하지 않도록 동시에 하나의 동기화만 실행하며, 실행 중 요청은 PolicySyncInProgressException으로 거절한다.
 * 동기화 후 검색용 청크 갱신(#180), 혜택 추출(#162), 청크 임베딩(#181)을 차례로 실행한다.
 * 청크 백필과 임베딩 백필(관리자 API)도 같은 잠금을 써서, 동기화가 지우는 정책의 청크를 다시 만들거나
 * 지워지는 청크를 임베딩하는 경합을 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicySyncOrchestrator {

    private final YouthCenterPolicySyncService youthCenterPolicySyncService;
    private final KinfaPolicySyncService kinfaPolicySyncService;
    private final PolicyBenefitExtractionService policyBenefitExtractionService;
    private final PolicyChunkService policyChunkService;
    private final PolicyChunkEmbeddingService policyChunkEmbeddingService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public PolicySyncResult syncAll() {
        if (!running.compareAndSet(false, true)) {
            throw new PolicySyncInProgressException();
        }
        try {
            PolicySyncResult result = doSyncAll();
            refreshChunks();
            extractBenefits();
            embedChunks();
            return result;
        } finally {
            running.set(false);
        }
    }

    /**
     * 검색용 청크를 즉시 갱신한다 (관리자 백필, #180). 동기화와 잠금을 공유하므로 동기화 중이면 PolicySyncInProgressException(409)
     *
     * @param all true면 원문이 같은 정책까지 전부 다시 만든다
     */
    public PolicyChunkRefreshResult refreshChunksNow(boolean all) {
        if (!running.compareAndSet(false, true)) {
            throw new PolicySyncInProgressException();
        }
        try {
            return all ? policyChunkService.refreshAll() : policyChunkService.refreshPending();
        } finally {
            running.set(false);
        }
    }

    /**
     * 청크 임베딩을 즉시 갱신한다 (관리자 백필, #181). 동기화와 잠금을 공유하므로 동기화 중이면 PolicySyncInProgressException(409)
     *
     * @param all true면 이미 임베딩된 청크까지 전부 다시 임베딩한다
     */
    public PolicyEmbeddingRefreshResult refreshEmbeddingsNow(boolean all) {
        if (!running.compareAndSet(false, true)) {
            throw new PolicySyncInProgressException();
        }
        try {
            return all ? policyChunkEmbeddingService.refreshAll() : policyChunkEmbeddingService.refreshPending();
        } finally {
            running.set(false);
        }
    }

    // 동기화로 새로 들어오거나 원문이 바뀐 정책의 청크를 다시 만든다 (#180).
    // 부가 작업이라 실패해도 동기화 결과에 영향을 주지 않는다 (다음 동기화 때 해시를 다시 비교해 만든다)
    private void refreshChunks() {
        try {
            policyChunkService.refreshPending();
        } catch (Exception e) {
            log.error("동기화 후 정책 청크 갱신 실패", e);
        }
    }

    // 동기화로 새로 들어오거나 원문이 바뀐 정책의 혜택을 추출한다 (#162).
    // 추출은 부가 작업이라 실패해도 동기화 결과에 영향을 주지 않는다 (실패한 정책은 다음 동기화 때 다시 시도)
    private void extractBenefits() {
        try {
            policyBenefitExtractionService.extractPending();
        } catch (BenefitExtractionInProgressException e) {
            log.warn("정책 혜택 추출(관리자 백필)이 이미 실행 중이어서 동기화 후 추출을 건너뜁니다");
        } catch (Exception e) {
            log.error("동기화 후 정책 혜택 추출 실패", e);
        }
    }

    // 새로 만든 청크를 임베딩한다 (#181). 부가 작업이라 실패해도 동기화 결과에 영향을 주지 않는다
    // (키가 없거나 임베딩 테이블이 아직 없어도 여기서 끝나고, 다음 동기화 때 다시 대상이 된다)
    private void embedChunks() {
        try {
            policyChunkEmbeddingService.refreshPending();
        } catch (Exception e) {
            log.error("동기화 후 정책 청크 임베딩 실패", e);
        }
    }

    private PolicySyncResult doSyncAll() {
        long start = System.currentTimeMillis();

        SourceResult youthCenterResult = runSync("YOUTH_CENTER",
                () -> youthCenterPolicySyncService.sync());
        SourceResult kinfaResult = runSync("KINFA",
                () -> kinfaPolicySyncService.sync());

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
