package kr.fintarget.api.domain.policy.service;

import kr.fintarget.api.domain.policy.benefit.BenefitExtractionInProgressException;
import kr.fintarget.api.domain.policy.benefit.PolicyBenefitExtractionService;
import kr.fintarget.api.domain.policy.chunk.PolicyChunkRefreshResult;
import kr.fintarget.api.domain.policy.chunk.PolicyChunkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicySyncOrchestratorTest {

    @Mock
    private YouthCenterPolicySyncService youthCenterPolicySyncService;

    @Mock
    private KinfaPolicySyncService kinfaPolicySyncService;

    @Mock
    private PolicyBenefitExtractionService policyBenefitExtractionService;

    @Mock
    private PolicyChunkService policyChunkService;

    @InjectMocks
    private PolicySyncOrchestrator policySyncOrchestrator;

    @Test
    void 두_소스_모두_성공하면_저장건수를_합산한다() {
        when(youthCenterPolicySyncService.sync()).thenReturn(3);
        when(kinfaPolicySyncService.sync()).thenReturn(5);

        PolicySyncResult result = policySyncOrchestrator.syncAll();

        assertThat(result.youthCenterSavedCount()).isEqualTo(3);
        assertThat(result.youthCenterFailed()).isFalse();
        assertThat(result.kinfaSavedCount()).isEqualTo(5);
        assertThat(result.kinfaFailed()).isFalse();
    }

    @Test
    void 온통청년이_실패해도_KINFA_동기화는_계속_실행된다() {
        when(youthCenterPolicySyncService.sync()).thenThrow(new RuntimeException("온통청년 API 오류"));
        when(kinfaPolicySyncService.sync()).thenReturn(7);

        PolicySyncResult result = policySyncOrchestrator.syncAll();

        assertThat(result.youthCenterFailed()).isTrue();
        assertThat(result.youthCenterSavedCount()).isZero();
        assertThat(result.kinfaFailed()).isFalse();
        assertThat(result.kinfaSavedCount()).isEqualTo(7);
        verify(kinfaPolicySyncService).sync();
    }

    @Test
    void KINFA가_실패해도_온통청년_동기화_결과는_유지된다() {
        when(youthCenterPolicySyncService.sync()).thenReturn(4);
        when(kinfaPolicySyncService.sync()).thenThrow(new RuntimeException("KINFA API 오류"));

        PolicySyncResult result = policySyncOrchestrator.syncAll();

        assertThat(result.youthCenterFailed()).isFalse();
        assertThat(result.youthCenterSavedCount()).isEqualTo(4);
        assertThat(result.kinfaFailed()).isTrue();
        assertThat(result.kinfaSavedCount()).isZero();
    }

    @Test
    void 동기화가_실행_중이면_새_동기화_요청을_거절한다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(youthCenterPolicySyncService.sync()).thenAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return 1;
        });
        when(kinfaPolicySyncService.sync()).thenReturn(2);

        CompletableFuture<PolicySyncResult> first = CompletableFuture.supplyAsync(policySyncOrchestrator::syncAll);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> policySyncOrchestrator.syncAll())
                .isInstanceOf(PolicySyncInProgressException.class);

        release.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS).youthCenterSavedCount()).isEqualTo(1);
    }

    @Test
    void 동기화가_끝나면_다시_실행할_수_있다() {
        when(youthCenterPolicySyncService.sync()).thenReturn(1);
        when(kinfaPolicySyncService.sync()).thenReturn(2);

        policySyncOrchestrator.syncAll();
        PolicySyncResult second = policySyncOrchestrator.syncAll();

        assertThat(second.kinfaSavedCount()).isEqualTo(2);
    }

    @Test
    void 동기화가_끝나면_혜택_추출을_실행한다() {
        when(youthCenterPolicySyncService.sync()).thenReturn(1);
        when(kinfaPolicySyncService.sync()).thenReturn(2);

        policySyncOrchestrator.syncAll();

        InOrder inOrder = inOrder(youthCenterPolicySyncService, kinfaPolicySyncService, policyBenefitExtractionService);
        inOrder.verify(youthCenterPolicySyncService).sync();
        inOrder.verify(kinfaPolicySyncService).sync();
        inOrder.verify(policyBenefitExtractionService).extractPending();
    }

    @Test
    void 동기화가_끝나면_청크를_갱신한_뒤_혜택을_추출한다() {
        when(youthCenterPolicySyncService.sync()).thenReturn(1);
        when(kinfaPolicySyncService.sync()).thenReturn(2);

        policySyncOrchestrator.syncAll();

        InOrder inOrder = inOrder(kinfaPolicySyncService, policyChunkService, policyBenefitExtractionService);
        inOrder.verify(kinfaPolicySyncService).sync();
        inOrder.verify(policyChunkService).refreshPending();
        inOrder.verify(policyBenefitExtractionService).extractPending();
    }

    @Test
    void 청크_갱신이_실패해도_혜택_추출과_동기화_결과는_그대로_진행된다() {
        when(youthCenterPolicySyncService.sync()).thenReturn(3);
        when(kinfaPolicySyncService.sync()).thenReturn(5);
        when(policyChunkService.refreshPending()).thenThrow(new RuntimeException("청크 오류"));

        PolicySyncResult result = policySyncOrchestrator.syncAll();

        assertThat(result.youthCenterSavedCount()).isEqualTo(3);
        assertThat(result.kinfaSavedCount()).isEqualTo(5);
        verify(policyBenefitExtractionService).extractPending();
    }

    @Test
    void 청크_백필은_all_값에_따라_변경분_또는_전체를_다시_만든다() {
        PolicyChunkRefreshResult pending = new PolicyChunkRefreshResult(10, 2, 8, 3, 0, 5L);
        PolicyChunkRefreshResult all = new PolicyChunkRefreshResult(10, 10, 0, 12, 0, 9L);
        when(policyChunkService.refreshPending()).thenReturn(pending);
        when(policyChunkService.refreshAll()).thenReturn(all);

        assertThat(policySyncOrchestrator.refreshChunksNow(false)).isEqualTo(pending);
        assertThat(policySyncOrchestrator.refreshChunksNow(true)).isEqualTo(all);
    }

    @Test
    void 동기화가_실행_중이면_청크_백필을_거절한다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(youthCenterPolicySyncService.sync()).thenAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return 1;
        });
        when(kinfaPolicySyncService.sync()).thenReturn(2);

        CompletableFuture<PolicySyncResult> sync = CompletableFuture.supplyAsync(policySyncOrchestrator::syncAll);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> policySyncOrchestrator.refreshChunksNow(false))
                .isInstanceOf(PolicySyncInProgressException.class);
        verify(policyChunkService, never()).refreshAll();

        release.countDown();
        sync.get(5, TimeUnit.SECONDS);
    }

    @Test
    void 청크_백필이_실행_중이면_동기화를_거절한다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(policyChunkService.refreshAll()).thenAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return new PolicyChunkRefreshResult(0, 0, 0, 0, 0, 0L);
        });

        CompletableFuture<PolicyChunkRefreshResult> refresh =
                CompletableFuture.supplyAsync(() -> policySyncOrchestrator.refreshChunksNow(true));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> policySyncOrchestrator.syncAll())
                .isInstanceOf(PolicySyncInProgressException.class);

        release.countDown();
        refresh.get(5, TimeUnit.SECONDS);
    }

    @Test
    void 혜택_추출이_실패해도_동기화_결과는_그대로_반환한다() {
        when(youthCenterPolicySyncService.sync()).thenReturn(3);
        when(kinfaPolicySyncService.sync()).thenReturn(5);
        when(policyBenefitExtractionService.extractPending()).thenThrow(new RuntimeException("추출 오류"));

        PolicySyncResult result = policySyncOrchestrator.syncAll();

        assertThat(result.youthCenterSavedCount()).isEqualTo(3);
        assertThat(result.kinfaSavedCount()).isEqualTo(5);
    }

    @Test
    void 관리자_백필이_실행_중이면_동기화_후_추출만_건너뛴다() {
        when(youthCenterPolicySyncService.sync()).thenReturn(1);
        when(kinfaPolicySyncService.sync()).thenReturn(1);
        when(policyBenefitExtractionService.extractPending()).thenThrow(new BenefitExtractionInProgressException());

        PolicySyncResult result = policySyncOrchestrator.syncAll();

        assertThat(result.youthCenterFailed()).isFalse();
        assertThat(result.kinfaFailed()).isFalse();
    }
}
