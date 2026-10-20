package kr.fintarget.api.domain.policy.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicySyncOrchestratorTest {

    @Mock
    private YouthCenterPolicySyncService youthCenterPolicySyncService;

    @Mock
    private KinfaPolicySyncService kinfaPolicySyncService;

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
}
