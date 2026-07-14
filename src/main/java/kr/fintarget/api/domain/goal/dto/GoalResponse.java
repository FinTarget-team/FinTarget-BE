package kr.fintarget.api.domain.goal.dto;

import kr.fintarget.api.domain.goal.entity.Goal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record GoalResponse(
        UUID goalId,
        String title,
        Long targetAmount,
        Long currentAmount,
        LocalDate deadline,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        double progressRate,
        long dday
) {
    public static GoalResponse from(Goal goal) {
        double progressRate = goal.getTargetAmount() == 0 ? 0.0
                : (double) goal.getCurrentAmount() / goal.getTargetAmount() * 100;

        long dday = ChronoUnit.DAYS.between(LocalDate.now(), goal.getDeadline());

        return new GoalResponse(
                goal.getGoalId(),
                goal.getTitle(),
                goal.getTargetAmount(),
                goal.getCurrentAmount(),
                goal.getDeadline(),
                goal.getStatus().name(),
                goal.getCreatedAt(),
                goal.getUpdatedAt(),
                progressRate,
                dday
        );
    }
}