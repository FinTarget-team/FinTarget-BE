package kr.fintarget.api.domain.simulation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.fintarget.api.domain.simulation.entity.Simulation;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record SimulationResponse(
    @Schema(description = "시뮬레이션 ID (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID simulationId,
    @Schema(description = "시뮬레이션 대상 목표 ID (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID goalId,
    @Schema(description = "요청에 사용된 월 저축액(원)", example = "500000")
    Long monthlySaving,
    @Schema(description = "몬테카를로 시뮬레이션 중앙값(50th percentile) 기준 예상 달성일", example = "2028-03-15")
    LocalDate expectedCompletionDate,
    @Schema(
            description = "정책 혜택을 반영한 예상 달성일. 요청에 userPolicyId를 보내지 않았다면 null입니다.",
            example = "2027-11-20",
            types = {"string", "null"}
    )
    LocalDate policyCompletionDate,
    @Schema(description = "낙관적 시나리오(10th percentile) 기준 예상 달성일", example = "2027-09-10")
    LocalDate optimisticCompletionDate,
    @Schema(description = "비관적 시나리오(90th percentile) 기준 예상 달성일", example = "2028-08-01")
    LocalDate pessimisticCompletionDate,
    @Schema(description = "목표 마감일까지 목표 금액을 달성할 확률(0.0~1.0). 1만 회 몬테카를로 시뮬레이션 기준", example = "0.73")
    Double successProbability,
    @Schema(description = "시뮬레이션 실행(저장) 시각", example = "2026-08-19T10:00:00")
    LocalDateTime createdAt
) {
    public static SimulationResponse from(Simulation simulation) {
        return new SimulationResponse(
            simulation.getSimulationId(),
            simulation.getGoal().getGoalId(),
            simulation.getMonthlySaving(),
            simulation.getExpectedCompletionDate(),
            simulation.getPolicyCompletionDate(),
            simulation.getOptimisticCompletionDate(),
            simulation.getPessimisticCompletionDate(),
            simulation.getSuccessProbability(),
            simulation.getCreatedAt()
        );
    }
}
