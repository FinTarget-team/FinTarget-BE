package kr.fintarget.api.domain.simulation.service;

import kr.fintarget.api.domain.goal.entity.Goal;
import kr.fintarget.api.domain.goal.repository.GoalRepository;
import kr.fintarget.api.domain.policy.entity.UserPolicy;
import kr.fintarget.api.domain.policy.repository.UserPolicyRepository;
import kr.fintarget.api.domain.simulation.dto.SimulationRequest;
import kr.fintarget.api.domain.simulation.dto.SimulationResponse;
import kr.fintarget.api.domain.simulation.entity.Simulation;
import kr.fintarget.api.domain.simulation.repository.SimulationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SimulationService {

    private final SimulationRepository simulationRepository;
    private final GoalRepository goalRepository;
    private final UserPolicyRepository userPolicyRepository;

    @Transactional
    public SimulationResponse runSimulation(UUID userId, SimulationRequest request) {
        Goal goal = goalRepository.findById(request.goalId())
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 목표입니다."));

        long remaining = goal.getTargetAmount() - goal.getCurrentAmount();
        long monthsNeeded = (long) Math.ceil((double) remaining / request.monthlySaving());
        LocalDate expectedDate = LocalDate.now().plusMonths(monthsNeeded);

        LocalDate policyDate = null;
        UserPolicy userPolicy = null;

        if (request.userPolicyId() != null) {
            userPolicy = userPolicyRepository.findById(request.userPolicyId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저 정책입니다."));

            long monthlyBenefit = userPolicy.getPolicy().getBenefitAmount() / 12;
            long totalMonthlySaving = request.monthlySaving() + monthlyBenefit;
            long policyMonthsNeeded = (long) Math.ceil((double) remaining / totalMonthlySaving);
            policyDate = LocalDate.now().plusMonths(policyMonthsNeeded);
        }

        Simulation simulation = new Simulation(
            userId,
            goal,
            userPolicy,
            request.monthlySaving(),
            expectedDate,
            policyDate
        );

        return SimulationResponse.from(simulationRepository.save(simulation));
    }

    @Transactional(readOnly = true)
    public List<SimulationResponse> getSimulations(UUID userId) {
        return simulationRepository.findByUserId(userId)
            .stream()
            .map(SimulationResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SimulationResponse> getSimulationsByGoal(UUID userId, UUID goalId) {
        return simulationRepository.findByUserIdAndGoalGoalId(userId, goalId)
            .stream()
            .map(SimulationResponse::from)
            .collect(Collectors.toList());
    }
}
