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
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SimulationService {

    private static final double DEFAULT_ANNUAL_INTEREST_RATE = 0.03;
    // SimulationRequest의 @DecimalMax와 값이 동기화되어야 함
    private static final double MAX_ANNUAL_INTEREST_RATE = 1.0;
    private static final double MONTHLY_SAVING_VOLATILITY = 0.20;
    private static final int MONTE_CARLO_ITERATIONS = 10_000;

    private final SimulationRepository simulationRepository;
    private final GoalRepository goalRepository;
    private final UserPolicyRepository userPolicyRepository;
    private final Random random;

    @Transactional
    public SimulationResponse runSimulation(UUID userId, SimulationRequest request) {
        Goal goal = goalRepository.findByGoalIdAndUserId(request.goalId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 목표입니다."));

        if (request.annualInterestRate() != null
                && (!Double.isFinite(request.annualInterestRate()) || request.annualInterestRate() > MAX_ANNUAL_INTEREST_RATE)) {
            throw new IllegalArgumentException("연이율은 0 이상 100%(1.0) 이하의 유한한 값이어야 합니다.");
        }

        long remaining = goal.getTargetAmount() - goal.getCurrentAmount();
        double annualRate = request.annualInterestRate() != null ? request.annualInterestRate() : DEFAULT_ANNUAL_INTEREST_RATE;
        double monthlyRate = annualRate / 12.0;
        long monthsToDeadline = ChronoUnit.MONTHS.between(LocalDate.now(), goal.getDeadline());

        MonteCarloResult baseResult = runMonteCarlo(remaining, request.monthlySaving(), monthlyRate, monthsToDeadline);
        LocalDate expectedCompletionDate = LocalDate.now().plusMonths(baseResult.p50Months());
        LocalDate optimisticCompletionDate = LocalDate.now().plusMonths(baseResult.p10Months());
        LocalDate pessimisticCompletionDate = LocalDate.now().plusMonths(baseResult.p90Months());

        LocalDate policyDate = null;
        UserPolicy userPolicy = null;

        if (request.userPolicyId() != null) {
            userPolicy = userPolicyRepository.findByUserPolicyIdAndUserId(request.userPolicyId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저 정책입니다."));

            long monthlyBenefit = userPolicy.getPolicy().getBenefitAmount() / 12;
            long totalMonthlySaving = request.monthlySaving() + monthlyBenefit;
            MonteCarloResult policyResult = runMonteCarlo(remaining, totalMonthlySaving, monthlyRate, monthsToDeadline);
            policyDate = LocalDate.now().plusMonths(policyResult.p50Months());
        }

        Simulation simulation = new Simulation(
            userId,
            goal,
            userPolicy,
            request.monthlySaving(),
            expectedCompletionDate,
            policyDate,
            optimisticCompletionDate,
            pessimisticCompletionDate,
            baseResult.successProbability()
        );

        return SimulationResponse.from(simulationRepository.save(simulation));
    }

    private MonteCarloResult runMonteCarlo(long remaining, long meanMonthlySaving, double monthlyRate, long monthsToDeadline) {
        long[] monthsNeeded = new long[MONTE_CARLO_ITERATIONS];
        int successCount = 0;

        for (int i = 0; i < MONTE_CARLO_ITERATIONS; i++) {
            double sampled = meanMonthlySaving + random.nextGaussian() * meanMonthlySaving * MONTHLY_SAVING_VOLATILITY;
            long sampledSaving = Math.max(1L, Math.round(sampled));
            long months = monthsNeeded(remaining, sampledSaving, monthlyRate);
            monthsNeeded[i] = months;
            if (months <= monthsToDeadline) {
                successCount++;
            }
        }

        Arrays.sort(monthsNeeded);
        double successProbability = (double) successCount / MONTE_CARLO_ITERATIONS;

        return new MonteCarloResult(
            percentile(monthsNeeded, 0.10),
            percentile(monthsNeeded, 0.50),
            percentile(monthsNeeded, 0.90),
            successProbability
        );
    }

    static long monthsNeeded(long remaining, long monthlySaving, double monthlyRate) {
        if (remaining <= 0) {
            return 0;
        }
        if (monthlyRate == 0.0) {
            return (long) Math.ceil((double) remaining / monthlySaving);
        }
        double growthFactor = 1 + (remaining * monthlyRate) / monthlySaving;
        return (long) Math.ceil(Math.log(growthFactor) / Math.log(1 + monthlyRate));
    }

    static long percentile(long[] sortedAscending, double p) {
        int index = (int) Math.ceil(p * sortedAscending.length) - 1;
        index = Math.max(0, Math.min(sortedAscending.length - 1, index));
        return sortedAscending[index];
    }

    private record MonteCarloResult(long p10Months, long p50Months, long p90Months, double successProbability) {}

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
