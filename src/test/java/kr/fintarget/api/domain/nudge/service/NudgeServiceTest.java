package kr.fintarget.api.domain.nudge.service;

import kr.fintarget.api.domain.expense.repository.ExpenseRepository;
import kr.fintarget.api.domain.goal.repository.GoalRepository;
import kr.fintarget.api.domain.nudge.entity.Nudge;
import kr.fintarget.api.domain.nudge.repository.NudgeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NudgeServiceTest {

    @Mock
    private NudgeRepository nudgeRepository;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private NudgeService nudgeService;

    private Nudge nudgeOf(String ownerId) {
        return Nudge.builder().id("nudge-1").userId(ownerId).type("GOAL_DEADLINE").message("메시지").build();
    }

    @Test
    void 본인_넛지는_읽음_처리된다() {
        Nudge nudge = nudgeOf("owner");
        when(nudgeRepository.findById("nudge-1")).thenReturn(Optional.of(nudge));

        nudgeService.markAsRead("owner", "nudge-1");

        assertThat(nudge.isRead()).isTrue();
    }

    // 401로 응답하면 앱이 토큰 만료로 오인해 로그아웃할 수 있고, 넛지 존재 여부도 드러난다 (#219)
    @Test
    void 다른_사용자의_넛지를_읽음_처리하면_없는_넛지와_같은_예외를_던진다() {
        Nudge nudge = nudgeOf("owner");
        when(nudgeRepository.findById("nudge-1")).thenReturn(Optional.of(nudge));

        assertThatThrownBy(() -> nudgeService.markAsRead("other", "nudge-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("넛지를 찾을 수 없습니다.");
        assertThat(nudge.isRead()).isFalse();
    }

    @Test
    void 다른_사용자의_넛지를_삭제하면_없는_넛지와_같은_예외를_던지고_삭제하지_않는다() {
        when(nudgeRepository.findById("nudge-1")).thenReturn(Optional.of(nudgeOf("owner")));

        assertThatThrownBy(() -> nudgeService.deleteNudge("other", "nudge-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("넛지를 찾을 수 없습니다.");
        verify(nudgeRepository, never()).delete(any());
    }

    @Test
    void 없는_넛지를_삭제하면_예외를_던진다() {
        when(nudgeRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> nudgeService.deleteNudge("owner", "missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("넛지를 찾을 수 없습니다.");
    }
}
