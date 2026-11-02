package kr.fintarget.api.domain.simulation.service;

import kr.fintarget.api.domain.goal.entity.Goal;
import kr.fintarget.api.domain.goal.repository.GoalRepository;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyBenefit;
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
import java.util.function.LongUnaryOperator;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SimulationService {

    private static final double DEFAULT_ANNUAL_INTEREST_RATE = 0.03;
    // SimulationRequest의 @DecimalMax와 값이 동기화되어야 함
    private static final double MAX_ANNUAL_INTEREST_RATE = 1.0;
    private static final double MONTHLY_SAVING_VOLATILITY = 0.20;
    private static final int MONTE_CARLO_ITERATIONS = 10_000;
    // 달성까지 걸리는 개월 수 상한(100년). 저축액이 목표에 비해 너무 작아도 계산이 끝나고,
    // LocalDate.plusMonths가 표현할 수 없는 날짜가 나오지 않도록 막는다 (#163)
    private static final int MAX_MONTHS = 1_200;

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

        // 월 저축액 표본을 먼저 한 번만 뽑아 기본 계산과 정책 계산에 같이 쓴다.
        // 흔들림(±20%)은 내 저축액에만 적용한다 — 정해진 지원금까지 흔들면 낙관·비관 범위가 실제보다 넓어진다 (#163)
        long[] savingSamples = sampleMonthlySavings(request.monthlySaving());

        MonteCarloResult baseResult = runMonteCarlo(savingSamples, monthsToDeadline,
                saving -> monthsNeeded(remaining, saving, monthlyRate));
        LocalDate expectedCompletionDate = LocalDate.now().plusMonths(baseResult.p50Months());
        LocalDate optimisticCompletionDate = LocalDate.now().plusMonths(baseResult.p10Months());
        LocalDate pessimisticCompletionDate = LocalDate.now().plusMonths(baseResult.p90Months());

        LocalDate policyDate = null;
        Double policySuccessProbability = null;
        UserPolicy userPolicy = null;

        if (request.userPolicyId() != null) {
            userPolicy = userPolicyRepository.findByUserPolicyIdAndUserId(request.userPolicyId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저 정책입니다."));

            // 금액으로 환산된 혜택이 없는 정책(대출·현물·서비스, 아직 추출되지 않은 정책)은
            // 정책 반영 계산만 건너뛰고 policyCompletionDate·policySuccessProbability를 null로 둔다
            Policy policy = userPolicy.getPolicy();
            if (policy.hasBenefitForSimulation()) {
                PolicyBenefit benefit = policy.getBenefit();
                MonteCarloResult policyResult = runMonteCarlo(savingSamples, monthsToDeadline,
                        saving -> monthsNeededWithBenefit(remaining, saving, monthlyRate, benefit));
                policyDate = LocalDate.now().plusMonths(policyResult.p50Months());
                policySuccessProbability = policyResult.successProbability();
            }
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
            baseResult.successProbability(),
            policySuccessProbability
        );

        return SimulationResponse.from(simulationRepository.save(simulation));
    }

    // 월 저축액 표본. 기본 계산과 정책 계산이 같은 표본을 써야 "정책을 넣으면 빨라진다"가 흔들림 때문에 뒤집히지 않는다
    private long[] sampleMonthlySavings(long meanMonthlySaving) {
        long[] samples = new long[MONTE_CARLO_ITERATIONS];
        for (int i = 0; i < MONTE_CARLO_ITERATIONS; i++) {
            double sampled = meanMonthlySaving + random.nextGaussian() * meanMonthlySaving * MONTHLY_SAVING_VOLATILITY;
            samples[i] = Math.max(1L, Math.round(sampled));
        }
        return samples;
    }

    private static MonteCarloResult runMonteCarlo(long[] savingSamples, long monthsToDeadline,
                                                  LongUnaryOperator monthsOf) {
        long[] monthsNeeded = new long[savingSamples.length];
        int successCount = 0;

        for (int i = 0; i < savingSamples.length; i++) {
            long months = monthsOf.applyAsLong(savingSamples[i]);
            monthsNeeded[i] = months;
            if (months <= monthsToDeadline) {
                successCount++;
            }
        }

        Arrays.sort(monthsNeeded);
        double successProbability = (double) successCount / savingSamples.length;

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
        long months;
        if (monthlyRate == 0.0) {
            months = (long) Math.ceil((double) remaining / monthlySaving);
        } else {
            double growthFactor = 1 + (remaining * monthlyRate) / monthlySaving;
            months = (long) Math.ceil(Math.log(growthFactor) / Math.log(1 + monthlyRate));
        }
        return Math.min(months, MAX_MONTHS);
    }

    /**
     * 지원 혜택이 있을 때 목표까지 걸리는 개월 수. 공식으로는 지원 기간이 끝나는 시점과 1회 지급을 표현할 수 없어
     * 한 달씩 진행한다 (#163). 한 달 안에서의 순서는 이자 → 내 저축액 → 지원금(지원 기간 안이면) → 1회 지급(첫 달)이다.
     */
    static long monthsNeededWithBenefit(long remaining, long monthlySaving, double monthlyRate, PolicyBenefit benefit) {
        if (remaining <= 0) {
            return 0;
        }
        double balance = 0;
        for (int month = 1; month <= MAX_MONTHS; month++) {
            balance += balance * monthlyRate;
            balance += monthlySaving;
            balance += benefit.amountForMonth(month);
            if (balance >= remaining) {
                return month;
            }
        }
        return MAX_MONTHS;
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
