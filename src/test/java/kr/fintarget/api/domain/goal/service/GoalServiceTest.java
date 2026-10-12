package kr.fintarget.api.domain.goal.service;

import kr.fintarget.api.domain.goal.dto.GoalCreateRequest;
import kr.fintarget.api.domain.goal.dto.GoalResponse;
import kr.fintarget.api.domain.goal.dto.GoalUpdateRequest;
import kr.fintarget.api.domain.goal.entity.Goal;
import kr.fintarget.api.domain.goal.repository.GoalRepository;
import kr.fintarget.api.domain.simulation.repository.SimulationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private SimulationRepository simulationRepository;

    @InjectMocks
    private GoalService goalService;

    @Test
    void 온보딩_목표가_없으면_기본값으로_새_목표를_생성한다() {
        UUID userId = UUID.randomUUID();
        when(goalRepository.findByUserId(userId)).thenReturn(Optional.empty());

        goalService.upsertFromOnboarding(userId, 30_000_000L);

        ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository).save(captor.capture());
        Goal saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTitle()).isEqualTo("내 목표");
        assertThat(saved.getTargetAmount()).isEqualTo(30_000_000L);
        assertThat(saved.getCurrentAmount()).isZero();
        assertThat(saved.getDeadline()).isEqualTo(LocalDate.now().plusYears(1));
        assertThat(saved.isDeadlineDefaulted()).isTrue();
    }

    @Test
    void 온보딩_재제출시_기존_목표의_금액만_갱신하고_새로_만들지_않는다() {
        UUID userId = UUID.randomUUID();
        LocalDate deadline = LocalDate.of(2028, 12, 31);
        Goal existing = new Goal(userId, "전세 자금", 10_000_000L, 2_000_000L, deadline);
        when(goalRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        goalService.upsertFromOnboarding(userId, 50_000_000L);

        verify(goalRepository, never()).save(any());
        assertThat(existing.getTargetAmount()).isEqualTo(50_000_000L);
        assertThat(existing.getTitle()).isEqualTo("전세 자금");
        assertThat(existing.getCurrentAmount()).isEqualTo(2_000_000L);
        assertThat(existing.getDeadline()).isEqualTo(deadline);
    }

    @Test
    void 온보딩_목표_금액이_0이하면_예외가_발생한다() {
        assertThatThrownBy(() -> goalService.upsertFromOnboarding(UUID.randomUUID(), 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("목표 금액은 0보다 커야 합니다");

        verify(goalRepository, never()).save(any());
    }

    @Test
    void 목표_금액이_상한을_넘으면_예외가_발생한다() {
        assertThatThrownBy(() -> goalService.upsertFromOnboarding(UUID.randomUUID(), Goal.MAX_TARGET_AMOUNT + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("목표 금액은 50억 원 이하로 입력해주세요");
    }

    @Test
    void 목표_금액이_정확히_상한이면_생성된다() {
        UUID userId = UUID.randomUUID();
        when(goalRepository.findByUserId(userId)).thenReturn(Optional.empty());

        goalService.upsertFromOnboarding(userId, Goal.MAX_TARGET_AMOUNT);

        verify(goalRepository).save(any(Goal.class));
    }

    @Test
    void 목표_생성_API도_상한을_넘으면_예외가_발생한다() {
        UUID userId = UUID.randomUUID();
        when(goalRepository.findByUserId(userId)).thenReturn(Optional.empty());

        GoalCreateRequest request = new GoalCreateRequest(
                "내 집 마련", Goal.MAX_TARGET_AMOUNT + 1, 0L, LocalDate.of(2030, 1, 1));

        assertThatThrownBy(() -> goalService.createGoal(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("목표 금액은 50억 원 이하로 입력해주세요");
        verify(goalRepository, never()).save(any());
    }

    @Test
    void 마감일을_바꿔_수정하면_기본_마감일_플래그가_해제된다() {
        UUID userId = UUID.randomUUID();
        Goal goal = Goal.createFromOnboarding(userId, 30_000_000L);
        when(goalRepository.findByUserId(userId)).thenReturn(Optional.of(goal));

        GoalResponse response = goalService.updateGoal(userId, new GoalUpdateRequest(
                "내 집 마련", 30_000_000L, 0L, LocalDate.of(2030, 6, 30)));

        assertThat(response.deadlineDefaulted()).isFalse();
    }

    @Test
    void 마감일을_그대로_두고_수정하면_기본_마감일_플래그가_유지된다() {
        UUID userId = UUID.randomUUID();
        Goal goal = Goal.createFromOnboarding(userId, 30_000_000L);
        when(goalRepository.findByUserId(userId)).thenReturn(Optional.of(goal));

        GoalResponse response = goalService.updateGoal(userId, new GoalUpdateRequest(
                "내 집 마련", 40_000_000L, 1_000_000L, goal.getDeadline()));

        assertThat(response.deadlineDefaulted()).isTrue();
    }
}
