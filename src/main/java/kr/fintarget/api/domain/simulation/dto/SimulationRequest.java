package kr.fintarget.api.domain.simulation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record SimulationRequest(
        @NotNull(message = "목표를 선택해주세요.")
        UUID goalId,

        @NotNull(message = "월 저축액을 입력해주세요.")
        @Positive(message = "월 저축액은 0보다 커야 합니다.")
        Long monthlySaving,

        UUID userPolicyId
) {}