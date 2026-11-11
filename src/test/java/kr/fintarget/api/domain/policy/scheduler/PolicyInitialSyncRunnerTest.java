package kr.fintarget.api.domain.policy.scheduler;

import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.policy.service.PolicySyncOrchestrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyInitialSyncRunnerTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PolicySyncOrchestrator policySyncOrchestrator;

    @InjectMocks
    private PolicyInitialSyncRunner policyInitialSyncRunner;

    @Test
    void 정책_테이블이_비어_있으면_기동_시_동기화를_실행한다() {
        when(policyRepository.count()).thenReturn(0L);

        policyInitialSyncRunner.syncIfEmpty();

        verify(policySyncOrchestrator).syncAll();
    }

    @Test
    void 정책_데이터가_있으면_기동_시_동기화를_생략한다() {
        when(policyRepository.count()).thenReturn(42L);

        policyInitialSyncRunner.syncIfEmpty();

        verify(policySyncOrchestrator, never()).syncAll();
    }
}
