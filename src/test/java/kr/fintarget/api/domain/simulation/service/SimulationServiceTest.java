package kr.fintarget.api.domain.simulation.service;

import kr.fintarget.api.domain.goal.entity.Goal;
import kr.fintarget.api.domain.goal.repository.GoalRepository;
import kr.fintarget.api.domain.policy.client.dto.KinfaItem;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyBenefit;
import kr.fintarget.api.domain.policy.mapper.KinfaPolicyMapper;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.entity.UserPolicy;
import kr.fintarget.api.domain.policy.repository.UserPolicyRepository;
import kr.fintarget.api.domain.simulation.dto.SimulationRequest;
import kr.fintarget.api.domain.simulation.dto.SimulationResponse;
import kr.fintarget.api.domain.simulation.entity.Simulation;
import kr.fintarget.api.domain.simulation.repository.SimulationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;
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

    private static PolicyBenefit benefit(Long monthlyAmount, Integer months, Long oneTimeAmount) {
        return PolicyBenefit.builder()
                .monthlyAmount(monthlyAmount)
                .benefitMonths(months)
                .oneTimeAmount(oneTimeAmount)
                .build();
    }

    // 혜택이 채워진 정책을 스크랩한 상태를 만든다
    private void stubPolicy(UUID userPolicyId, PolicyBenefit benefit) {
        Policy policy = Policy.create("청년 저축 지원", "설명", null, null, null, null,
                PolicyType.FINANCE_WELFARE, "전국", null, null);
        if (benefit != null) {
            policy.updateBenefit(benefit);
        }
        UserPolicy userPolicy = new UserPolicy(USER_ID, policy, UserPolicy.UserPolicyStatus.APPLIED);
        when(userPolicyRepository.findByUserPolicyIdAndUserId(userPolicyId, USER_ID))
                .thenReturn(Optional.of(userPolicy));
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
        stubPolicy(userPolicyId, benefit(200_000L, 24, null));

        SimulationRequest request = new SimulationRequest(UUID.randomUUID(), 100_000L, userPolicyId, null);
        SimulationResponse response = simulationService.runSimulation(USER_ID, request);

        assertThat(response.policyCompletionDate()).isBeforeOrEqualTo(response.expectedCompletionDate());
        // 정책 반영 확률도 함께 계산된다 (#163). 기본보다 낮아질 수는 없다
        assertThat(response.policySuccessProbability()).isNotNull()
                .isGreaterThanOrEqualTo(response.successProbability());
    }

    @Test
    void 정책_혜택_금액이_없으면_정책_완료일만_null이고_기본_결과는_정상_계산된다() {
        Goal goal = new Goal(USER_ID, "정책 활용 목표", 5_000_000L, 0L, LocalDate.now().plusMonths(36));
        when(goalRepository.findByGoalIdAndUserId(any(), eq(USER_ID)))
                .thenReturn(Optional.of(goal));

        UUID userPolicyId = UUID.randomUUID();
        Policy policy = Policy.create("청년 월세 지원", "설명", null, null, null, null, PolicyType.HOUSING, "전국", null, null);
        UserPolicy userPolicy = new UserPolicy(USER_ID, policy, UserPolicy.UserPolicyStatus.APPLIED);
        when(userPolicyRepository.findByUserPolicyIdAndUserId(userPolicyId, USER_ID))
                .thenReturn(Optional.of(userPolicy));

        SimulationRequest request = new SimulationRequest(UUID.randomUUID(), 100_000L, userPolicyId, null);
        SimulationResponse response = simulationService.runSimulation(USER_ID, request);

        assertThat(response.policyCompletionDate()).isNull();
        assertThat(response.policySuccessProbability()).isNull();
        assertThat(response.expectedCompletionDate()).isNotNull();
        assertThat(response.optimisticCompletionDate()).isNotNull();
        assertThat(response.pessimisticCompletionDate()).isNotNull();
        assertThat(response.successProbability()).isNotNull();

        ArgumentCaptor<Simulation> captor = ArgumentCaptor.forClass(Simulation.class);
        verify(simulationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserPolicy()).isSameAs(userPolicy);
    }

    // 지원 기간이 끝나면 지원금이 더 이상 더해지지 않는다 (#163 이슈 본문 예시)
    // 남은 2,000만원·월 30만원 저축·이율 0·월 20만원 24개월 지원
    //  → 24개월간 월 50만원으로 1,200만원, 남은 800만원을 월 30만원으로 27개월 = 51개월
    @Test
    void monthsNeededWithBenefit_지원_기간이_끝나면_지원금이_더해지지_않는다() {
        long months = SimulationService.monthsNeededWithBenefit(
                20_000_000L, 300_000L, 0.0, benefit(200_000L, 24, null));

        assertThat(months).isEqualTo(51L);
    }

    @Test
    void monthsNeededWithBenefit_지원_기간이_없으면_기본_12개월만_더해진다() {
        // 같은 조건에서 기간이 원문에 없으면 12개월만 반영 (#162)
        //  → 12개월간 월 50만원으로 600만원, 남은 1,400만원을 월 30만원으로 47개월 = 59개월
        //    (계속 더하면 40개월로 지나치게 빨라진다)
        long months = SimulationService.monthsNeededWithBenefit(
                20_000_000L, 300_000L, 0.0, benefit(200_000L, null, null));

        assertThat(months).isEqualTo(59L);
    }

    @Test
    void monthsNeededWithBenefit_1회_지급은_첫_달에_한_번만_더해진다() {
        // 남은 1,000만원·월 100만원 저축·이율 0·1회 300만원 → 첫 달 400만원, 이후 월 100만원 → 7개월
        long months = SimulationService.monthsNeededWithBenefit(
                10_000_000L, 1_000_000L, 0.0, benefit(null, null, 3_000_000L));

        assertThat(months).isEqualTo(7L);
        // 1회 지급이 매달 들어간다면 1,000만원 / 400만원 = 3개월이 되어야 한다
        assertThat(months).isNotEqualTo(3L);
    }

    @Test
    void monthsNeededWithBenefit_달성에_너무_오래_걸리면_상한_개월_수를_돌려준다() {
        long months = SimulationService.monthsNeededWithBenefit(
                Long.MAX_VALUE / 2, 1L, 0.0, benefit(1L, 1, null));

        assertThat(months).isEqualTo(1_200L);
    }

    // 정책을 골라도 기본 결과(정책 미반영)는 달라지지 않아야 한다 (#163).
    // 저축액 표본을 기본·정책 계산이 공유하므로, 정책 계산이 난수 순서를 흔들면 이 테스트가 깨진다
    @Test
    void 정책을_골라도_기본_결과는_정책_없이_계산한_값과_같다() {
        Goal goal = new Goal(USER_ID, "정책 활용 목표", 5_000_000L, 0L, LocalDate.now().plusMonths(36));
        when(goalRepository.findByGoalIdAndUserId(any(), eq(USER_ID)))
                .thenReturn(Optional.of(goal));
        UUID goalId = UUID.randomUUID();

        SimulationResponse withoutPolicy = simulationService.runSimulation(
                USER_ID, new SimulationRequest(goalId, 100_000L, null, null));

        // 같은 시드로 다시 시작해 정책만 추가한다
        simulationService = new SimulationService(simulationRepository, goalRepository, userPolicyRepository, new Random(42L));
        UUID userPolicyId = UUID.randomUUID();
        stubPolicy(userPolicyId, benefit(200_000L, 24, null));
        SimulationResponse withPolicy = simulationService.runSimulation(
                USER_ID, new SimulationRequest(goalId, 100_000L, userPolicyId, null));

        assertThat(withPolicy.expectedCompletionDate()).isEqualTo(withoutPolicy.expectedCompletionDate());
        assertThat(withPolicy.optimisticCompletionDate()).isEqualTo(withoutPolicy.optimisticCompletionDate());
        assertThat(withPolicy.pessimisticCompletionDate()).isEqualTo(withoutPolicy.pessimisticCompletionDate());
        assertThat(withPolicy.successProbability()).isEqualTo(withoutPolicy.successProbability());

        // 정책을 안 골랐을 때는 정책 반영 결과가 없다
        assertThat(withoutPolicy.policyCompletionDate()).isNull();
        assertThat(withoutPolicy.policySuccessProbability()).isNull();
    }

    // 매퍼를 거쳐 만든 KINFA 정책(대출 상품)이 시뮬레이션에서 저축 혜택으로 계산되지 않는지 확인한다 (#151)
    @Test
    void KINFA_대출_정책은_정상_실행되고_정책_완료일만_null이다() {
        Goal goal = new Goal(USER_ID, "전세 자금 목표", 5_000_000L, 0L, LocalDate.now().plusMonths(36));
        when(goalRepository.findByGoalIdAndUserId(any(), eq(USER_ID)))
                .thenReturn(Optional.of(goal));

        UUID userPolicyId = UUID.randomUUID();
        Policy kinfaPolicy = KinfaPolicyMapper.toPolicy(new KinfaItem(
                "202609", "1", "버팀목 전세자금대출", "2억원", "변동금리", "연 2.5%", "10년", "-", "-",
                "원리금균등분할상환", "주거", "무주택 세대주", "기금", "주택도시기금", "전국",
                "지원조건", "없음", "없음", "은행 문의", "Y", "1", "대출상품", null, null, null, null, null
        ));
        UserPolicy userPolicy = new UserPolicy(USER_ID, kinfaPolicy, UserPolicy.UserPolicyStatus.APPLIED);
        when(userPolicyRepository.findByUserPolicyIdAndUserId(userPolicyId, USER_ID))
                .thenReturn(Optional.of(userPolicy));

        SimulationRequest request = new SimulationRequest(UUID.randomUUID(), 100_000L, userPolicyId, null);
        SimulationResponse response = simulationService.runSimulation(USER_ID, request);

        // 2억 한도가 benefitAmount로 들어가면 월 1,667만원 저축으로 계산돼 목표가 즉시 달성된 것처럼 보인다
        assertThat(kinfaPolicy.getBenefitAmount()).isNull();
        assertThat(kinfaPolicy.hasBenefitForSimulation()).isFalse();
        assertThat(response.policyCompletionDate()).isNull();
        assertThat(response.policySuccessProbability()).isNull();
        assertThat(response.expectedCompletionDate()).isNotNull();
        assertThat(response.successProbability()).isNotNull();
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
