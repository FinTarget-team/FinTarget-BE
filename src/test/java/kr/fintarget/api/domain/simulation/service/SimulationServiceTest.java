package kr.fintarget.api.domain.simulation.service;

import kr.fintarget.api.domain.goal.entity.Goal;
import kr.fintarget.api.domain.goal.repository.GoalRepository;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.entity.UserPolicy;
import kr.fintarget.api.domain.policy.repository.UserPolicyRepository;
import kr.fintarget.api.domain.simulation.dto.SimulationRequest;
import kr.fintarget.api.domain.simulation.dto.SimulationResponse;
import kr.fintarget.api.domain.simulation.repository.SimulationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulationServiceTest {

    @Mock
    private SimulationRepository simulationRepository;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private UserPolicyRepository userPolicyRepository;

    private SimulationService simulationService;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // 고정 시드: 몬테카를로 결과가 재현 가능하도록
        simulationService = new SimulationService(simulationRepository, goalRepository, userPolicyRepository, new Random(42L));
        lenient().when(simulationRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void monthsNeeded_이자율이_0이면_기존_선형_계산과_동일하다() {
        assertThat(SimulationService.monthsNeeded(1_000_000L, 100_000L, 0.0)).isEqualTo(10L);
        assertThat(SimulationService.monthsNeeded(1_050_000L, 100_000L, 0.0)).isEqualTo(11L); // ceil
    }

    @Test
    void monthsNeeded_이자율이_있으면_선형_계산보다_기간이_짧거나_같다() {
        long linear = SimulationService.monthsNeeded(5_000_000L, 200_000L, 0.0);
        long withInterest = SimulationService.monthsNeeded(5_000_000L, 200_000L, 0.0025);

        assertThat(withInterest).isLessThanOrEqualTo(linear);
    }

    @Test
    void monthsNeeded_남은_금액이_0이하면_0을_반환한다() {
        assertThat(SimulationService.monthsNeeded(0L, 100_000L, 0.0025)).isEqualTo(0L);
        assertThat(SimulationService.monthsNeeded(-1L, 100_000L, 0.0025)).isEqualTo(0L);
    }

    @Test
    void percentile_정렬된_배열에서_올바른_인덱스를_반환한다() {
        long[] sorted = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        assertThat(SimulationService.percentile(sorted, 0.10)).isEqualTo(1L);
        assertThat(SimulationService.percentile(sorted, 0.50)).isEqualTo(5L);
        assertThat(SimulationService.percentile(sorted, 0.90)).isEqualTo(9L);
    }

    @Test
    void 낙관적_중앙값_비관적_완료일은_순서대로_정렬된다() {
        Goal goal = new Goal(USER_ID, "내 집 마련", 1_200_000L, 0L, LocalDate.now().plusMonths(24));
        when(goalRepository.findByGoalIdAndUserId(any(), eq(USER_ID)))
                .thenReturn(Optional.of(goal));

        SimulationRequest request = new SimulationRequest(UUID.randomUUID(), 100_000L, null, null);
        SimulationResponse response = simulationService.runSimulation(USER_ID, request);

        assertThat(response.optimisticCompletionDate()).isBeforeOrEqualTo(response.expectedCompletionDate());
        assertThat(response.expectedCompletionDate()).isBeforeOrEqualTo(response.pessimisticCompletionDate());
        assertThat(response.successProbability()).isBetween(0.0, 1.0);
    }

    @Test
    void 목표까지_여유가_충분하면_성공_확률이_높다() {
        Goal goal = new Goal(USER_ID, "여유있는 목표", 1_200_000L, 0L, LocalDate.now().plusMonths(24));
        when(goalRepository.findByGoalIdAndUserId(any(), eq(USER_ID)))
                .thenReturn(Optional.of(goal));

        SimulationRequest request = new SimulationRequest(UUID.randomUUID(), 100_000L, null, null);
        SimulationResponse response = simulationService.runSimulation(USER_ID, request);

        assertThat(response.successProbability()).isGreaterThan(0.9);
    }

    @Test
    void 목표까지_여유가_없으면_성공_확률이_낮다() {
        Goal goal = new Goal(USER_ID, "촉박한 목표", 10_000_000L, 0L, LocalDate.now().plusMonths(3));
        when(goalRepository.findByGoalIdAndUserId(any(), eq(USER_ID)))
                .thenReturn(Optional.of(goal));

        SimulationRequest request = new SimulationRequest(UUID.randomUUID(), 100_000L, null, null);
        SimulationResponse response = simulationService.runSimulation(USER_ID, request);

        assertThat(response.successProbability()).isLessThan(0.1);
    }

    @Test
    void 정책_혜택이_있으면_예상_완료일이_더_빠르거나_같다() {
        Goal goal = new Goal(USER_ID, "정책 활용 목표", 5_000_000L, 0L, LocalDate.now().plusMonths(36));
        when(goalRepository.findByGoalIdAndUserId(any(), eq(USER_ID)))
                .thenReturn(Optional.of(goal));

        UUID userPolicyId = UUID.randomUUID();
        Policy policy = Policy.create("청년 저축 지원", "설명", null, null, null, 2_400_000L, PolicyType.FINANCE_WELFARE, "전국", null, null);
        UserPolicy userPolicy = new UserPolicy(USER_ID, policy, UserPolicy.UserPolicyStatus.APPLIED);
        when(userPolicyRepository.findByUserPolicyIdAndUserId(userPolicyId, USER_ID))
                .thenReturn(Optional.of(userPolicy));

        SimulationRequest request = new SimulationRequest(UUID.randomUUID(), 100_000L, userPolicyId, null);
        SimulationResponse response = simulationService.runSimulation(USER_ID, request);

        assertThat(response.policyCompletionDate()).isBeforeOrEqualTo(response.expectedCompletionDate());
    }

    @Test
    void 연이율이_무한대이면_예외가_발생한다() {
        Goal goal = new Goal(USER_ID, "목표", 1_200_000L, 0L, LocalDate.now().plusMonths(24));
        when(goalRepository.findByGoalIdAndUserId(any(), eq(USER_ID)))
                .thenReturn(Optional.of(goal));

        SimulationRequest request = new SimulationRequest(UUID.randomUUID(), 100_000L, null, Double.POSITIVE_INFINITY);

        assertThatThrownBy(() -> simulationService.runSimulation(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 연이율이_NaN이면_예외가_발생한다() {
        Goal goal = new Goal(USER_ID, "목표", 1_200_000L, 0L, LocalDate.now().plusMonths(24));
        when(goalRepository.findByGoalIdAndUserId(any(), eq(USER_ID)))
                .thenReturn(Optional.of(goal));

        SimulationRequest request = new SimulationRequest(UUID.randomUUID(), 100_000L, null, Double.NaN);

        assertThatThrownBy(() -> simulationService.runSimulation(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 연이율이_상한을_초과하면_예외가_발생한다() {
        Goal goal = new Goal(USER_ID, "목표", 1_200_000L, 0L, LocalDate.now().plusMonths(24));
        when(goalRepository.findByGoalIdAndUserId(any(), eq(USER_ID)))
                .thenReturn(Optional.of(goal));

        SimulationRequest request = new SimulationRequest(UUID.randomUUID(), 100_000L, null, 2.0);

        assertThatThrownBy(() -> simulationService.runSimulation(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 연이율이_유효한_범위이면_정상_계산된다() {
        Goal goal = new Goal(USER_ID, "목표", 1_200_000L, 0L, LocalDate.now().plusMonths(24));
        when(goalRepository.findByGoalIdAndUserId(any(), eq(USER_ID)))
                .thenReturn(Optional.of(goal));

        SimulationRequest request = new SimulationRequest(UUID.randomUUID(), 100_000L, null, 0.05);

        SimulationResponse response = simulationService.runSimulation(USER_ID, request);

        assertThat(response.expectedCompletionDate()).isNotNull();
    }
}
