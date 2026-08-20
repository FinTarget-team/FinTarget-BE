package kr.fintarget.api.domain.goal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record GoalCreateRequest(
    @Schema(description = "목표 제목", example = "내 집 마련")
    String title,
    @Schema(description = "목표 금액(원)", example = "50000000")
    Long targetAmount,
    @Schema(description = "현재까지 모은 금액(원)", example = "5000000")
    Long currentAmount,
    @Schema(description = "목표 달성 기한 (yyyy-MM-dd)", example = "2028-12-31")
    LocalDate deadline
) {}
