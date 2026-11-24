package kr.fintarget.api.domain.policy.recommend.rerank;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.recommend.LlmPolicyRecommendationSelector;
import kr.fintarget.api.domain.policy.recommend.LlmPolicyRecommendationSelector.RefreshOutcome;
import kr.fintarget.api.domain.policy.service.PolicyService;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.event.UserProfileChangedEvent;
import kr.fintarget.api.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyRerankPrecomputerTest {

    private final LlmPolicyRecommendationSelector selector = mock(LlmPolicyRecommendationSelector.class);
    private final PolicyService policyService = mock(PolicyService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final QueueExecutor executor = new QueueExecutor();
    private final PolicyRerankPrecomputer precomputer =
            new PolicyRerankPrecomputer(selector, policyService, userRepository, transactionManager, 0, executor);

    {
        TransactionStatus status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(status);
    }

    private static User user(String id) {
        return User.builder().id(id).provider(SocialProvider.KAKAO).providerId("p-" + id).region("SEOUL").income(1_000_000L).build();
    }

    @Test
    void 리랭킹을_쓸_수_없으면_아무것도_예약하지_않는다() {
        when(selector.isAvailable()).thenReturn(false);

        assertThat(precomputer.requestAll()).isFalse();
        assertThat(precomputer.requestUser("u1")).isFalse();
        precomputer.onApplicationReady();
        precomputer.onUserProfileChanged(new UserProfileChangedEvent("u1"));

        assertThat(executor.tasks).isEmpty();
    }

    @Test
    void 전체_미리_계산은_온보딩을_마친_사용자마다_전체_유형_후보로_refresh한다() {
        when(selector.isAvailable()).thenReturn(true);
        when(userRepository.findIdsByOnboardingCompletedTrue()).thenReturn(List.of("u1", "u2"));
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1")));
        when(userRepository.findById("u2")).thenReturn(Optional.of(user("u2")));
        List<Policy> sorted = List.of();
        when(policyService.sortedCandidatePolicies(any(), isNull(), any())).thenReturn(sorted);
        // 첫 사용자를 계산하는 중에 다시 요청되면(동기화 완료 등) 끝난 뒤 한 번 더 돈다
        java.util.concurrent.atomic.AtomicBoolean requestedAgain = new java.util.concurrent.atomic.AtomicBoolean();
        when(selector.refresh(any(), eq(sorted), any())).thenAnswer(invocation -> {
            if (requestedAgain.compareAndSet(false, true)) {
                assertThat(precomputer.requestAll()).isTrue();
            }
            return RefreshOutcome.COMPUTED;
        });

        assertThat(precomputer.requestAll()).isTrue();
        executor.runAll();

        verify(selector, times(4)).refresh(any(), eq(sorted), any());
        assertThat(precomputer.isRunningAll()).isFalse();
    }

    @Test
    void 프로필_변경_이벤트는_그_사용자만_미리_계산한다() {
        when(selector.isAvailable()).thenReturn(true);
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1")));
        when(policyService.sortedCandidatePolicies(any(), isNull(), any())).thenReturn(List.of());

        precomputer.onUserProfileChanged(new UserProfileChangedEvent("u1"));
        executor.runAll();

        verify(selector).refresh(any(), any(), any());
        verify(userRepository, never()).findIdsByOnboardingCompletedTrue();
    }

    @Test
    void 없는_사용자나_조회_실패는_예외_없이_건너뛴다() {
        when(userRepository.findById("gone")).thenReturn(Optional.empty());
        when(userRepository.findById("broken")).thenThrow(new IllegalStateException("db"));

        assertThat(precomputer.refreshUser("gone")).isEqualTo(RefreshOutcome.NOT_NEEDED);
        assertThat(precomputer.refreshUser("broken")).isEqualTo(RefreshOutcome.FAILED);
        verify(selector, never()).refresh(any(), any(), any());
    }

    // 제출된 작업을 쌓아 두었다가 테스트에서 순서대로 실행한다
    private static class QueueExecutor extends AbstractExecutorService {
        private final List<Runnable> tasks = new ArrayList<>();

        void runAll() {
            while (!tasks.isEmpty()) {
                tasks.removeFirst().run();
            }
        }

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
