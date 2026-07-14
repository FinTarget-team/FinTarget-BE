package kr.fintarget.api.domain.simulation.dto;

import kr.fintarget.api.domain.simulation.entity.Simulation;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record SimulationResponse(
    UUID simulationId,
    UUID goalId,
    Long monthlySaving,
    LocalDate expectedCompletionDate,
    LocalDate policyCompletionDate,
    LocalDateTime createdAt
) {
    public static SimulationResponse from(Simulation simulation) {
        return new SimulationResponse(
            simulation.getSimulationId(),
            simulation.getGoal().getGoalId(),
            simulation.getMonthlySaving(),
            simulation.getExpectedCompletionDate(),
            simulation.getPolicyCompletionDate(),
            simulation.getCreatedAt()
        );
    }
}
