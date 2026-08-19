package kr.fintarget.api.domain.goal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.fintarget.api.domain.goal.entity.Goal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record GoalResponse(
        @Schema(description = "목표 ID (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID goalId,
        @Schema(description = "목표 제목", example = "내 집 마련")
        String title,
        @Schema(description = "목표 금액(원)", example = "50000000")
        Long targetAmount,
        @Schema(description = "현재까지 모은 금액(원)", example = "8000000")
        Long currentAmount,
        @Schema(description = "목표 달성 기한 (yyyy-MM-dd)", example = "2028-12-31")
        LocalDate deadline,
        @Schema(description = "목표 상태", example = "ACTIVE", allowableValues = {"ACTIVE", "COMPLETED", "ABANDONED"})
        String status,
        @Schema(description = "목표 생성 시각", example = "2026-01-10T09:00:00")
        LocalDateTime createdAt,
        @Schema(description = "목표 최종 수정 시각", example = "2026-08-01T15:30:00")
        LocalDateTime updatedAt,
        @Schema(description = "달성률(%). currentAmount / targetAmount * 100으로 계산되며 목표 금액을 초과 달성하면 100을 넘을 수 있습니다.", example = "16.0")
        double progressRate,
        @Schema(description = "마감일까지 남은 일수. 마감일이 지났으면 음수가 될 수 있습니다.", example = "365")
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