package kr.fintarget.api.domain.simulation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record SimulationRequest(
        @NotNull(message = "목표를 선택해주세요.")
        UUID goalId,

        @NotNull(message = "월 저축액을 입력해주세요.")
        @Positive(message = "월 저축액은 0보다 커야 합니다.")
        Long monthlySaving,

        UUID userPolicyId,

        @PositiveOrZero(message = "연이율은 0 이상이어야 합니다.")
        // SimulationService.MAX_ANNUAL_INTEREST_RATE와 값이 동기화되어야 함
        @DecimalMax(value = "1.0", message = "연이율은 100%(1.0) 이하여야 합니다.")
        Double annualInterestRate
) {}