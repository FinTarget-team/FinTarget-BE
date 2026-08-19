package kr.fintarget.api.domain.simulation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record SimulationRequest(
        @Schema(description = "시뮬레이션 대상 목표 ID (UUID). 필수이며, 본인 소유의 목표여야 합니다.", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        @NotNull(message = "목표를 선택해주세요.")
        UUID goalId,

        @Schema(description = "월 저축액(원). 0보다 커야 합니다.", example = "500000")
        @NotNull(message = "월 저축액을 입력해주세요.")
        @Positive(message = "월 저축액은 0보다 커야 합니다.")
        Long monthlySaving,

        @Schema(
                description = "함께 반영할 사용자 정책 스크랩 ID (UUID). 생략하면 정책 혜택을 반영하지 않은 결과만 계산되며, "
                        + "응답의 policyCompletionDate도 null이 됩니다.",
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                types = {"string", "null"}
        )
        UUID userPolicyId,

        @Schema(
                description = "연이율(0.0~1.0, 예: 3% = 0.03). 생략하면 기본값 0.03(연 3%)이 적용됩니다.",
                example = "0.03",
                types = {"number", "null"}
        )
        @PositiveOrZero(message = "연이율은 0 이상이어야 합니다.")
        // SimulationService.MAX_ANNUAL_INTEREST_RATE와 값이 동기화되어야 함
        @DecimalMax(value = "1.0", message = "연이율은 100%(1.0) 이하여야 합니다.")
        Double annualInterestRate
) {}