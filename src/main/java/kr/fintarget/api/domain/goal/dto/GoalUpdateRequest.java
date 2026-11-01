package kr.fintarget.api.domain.goal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

public record GoalUpdateRequest(
    @Schema(description = "목표 제목. 부분 수정이 아니므로 항상 값을 보내야 합니다.", example = "내 집 마련")
    @NotBlank(message = "목표 제목을 입력해주세요.")
    String title,
    @Schema(description = "목표 금액(원). 1원 이상 50억 원 이하", example = "50000000")
    Long targetAmount,
    @Schema(description = "현재까지 모은 금액(원). 0 이상", example = "8000000")
    @NotNull(message = "현재 모은 금액을 입력해주세요.")
    @PositiveOrZero(message = "현재 모은 금액은 0 이상이어야 합니다.")
    Long currentAmount,
    @Schema(description = "목표 달성 기한 (yyyy-MM-dd)", example = "2028-12-31")
    @NotNull(message = "목표 기한을 입력해주세요.")
    LocalDate deadline
) {}
