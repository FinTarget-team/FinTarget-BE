package kr.fintarget.api.domain.policy.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
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
        when(youthCenterPolicySyncService.sync(1, 100)).thenReturn(3);
        when(kinfaPolicySyncService.sync(1, 100)).thenReturn(5);

        PolicySyncResult result = policySyncOrchestrator.syncAll();

        assertThat(result.youthCenterSavedCount()).isEqualTo(3);
        assertThat(result.youthCenterFailed()).isFalse();
        assertThat(result.kinfaSavedCount()).isEqualTo(5);
        assertThat(result.kinfaFailed()).isFalse();
    }

    @Test
    void 온통청년이_실패해도_KINFA_동기화는_계속_실행된다() {
        when(youthCenterPolicySyncService.sync(1, 100)).thenThrow(new RuntimeException("온통청년 API 오류"));
        when(kinfaPolicySyncService.sync(1, 100)).thenReturn(7);

        PolicySyncResult result = policySyncOrchestrator.syncAll();

        assertThat(result.youthCenterFailed()).isTrue();
        assertThat(result.youthCenterSavedCount()).isZero();
        assertThat(result.kinfaFailed()).isFalse();
        assertThat(result.kinfaSavedCount()).isEqualTo(7);
        verify(kinfaPolicySyncService).sync(1, 100);
    }

    @Test
    void KINFA가_실패해도_온통청년_동기화_결과는_유지된다() {
        when(youthCenterPolicySyncService.sync(1, 100)).thenReturn(4);
        when(kinfaPolicySyncService.sync(1, 100)).thenThrow(new RuntimeException("KINFA API 오류"));

        PolicySyncResult result = policySyncOrchestrator.syncAll();

        assertThat(result.youthCenterFailed()).isFalse();
        assertThat(result.youthCenterSavedCount()).isEqualTo(4);
        assertThat(result.kinfaFailed()).isTrue();
        assertThat(result.kinfaSavedCount()).isZero();
    }
}
