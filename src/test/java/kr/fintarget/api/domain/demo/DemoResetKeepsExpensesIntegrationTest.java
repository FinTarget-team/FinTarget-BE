package kr.fintarget.api.domain.demo;

import kr.fintarget.api.domain.demo.dto.DemoResetResult;
import kr.fintarget.api.domain.expense.entity.Expense;
import kr.fintarget.api.domain.expense.repository.ExpenseRepository;
import kr.fintarget.api.domain.goal.entity.Goal;
import kr.fintarget.api.domain.goal.repository.GoalRepository;
import kr.fintarget.api.domain.nudge.dto.NudgeListResponse;
import kr.fintarget.api.domain.nudge.dto.NudgeResponse;
import kr.fintarget.api.domain.nudge.entity.Nudge;
import kr.fintarget.api.domain.nudge.repository.NudgeRepository;
import kr.fintarget.api.domain.nudge.service.NudgeService;
import kr.fintarget.api.domain.onboarding.dto.OnboardingAnswerRequest;
import kr.fintarget.api.domain.onboarding.service.OnboardingService;
import kr.fintarget.api.domain.simulation.dto.SimulationRequest;
import kr.fintarget.api.domain.simulation.dto.SimulationResponse;
import kr.fintarget.api.domain.simulation.service.SimulationService;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import kr.fintarget.api.domain.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 지출을 남기는 초기화(#239)에서, 남은 지출을 쓰는 파생 데이터가 정상 동작하는지 확인한다.
 * 넛지는 지워지므로 남은 지출로 이상 지출 넛지가 다시 만들어질 수 있는데, 하루 한 번으로 묶여 반복되지 않아야 한다.
 */
@SpringBootTest
class DemoResetKeepsExpensesIntegrationTest {

    @Autowired
    private DemoResetService demoResetService;
    @Autowired
    private NudgeService nudgeService;
    @Autowired
    private SimulationService simulationService;
    @Autowired
    private OnboardingService onboardingService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private ExpenseRepository expenseRepository;
    @Autowired
    private GoalRepository goalRepository;
    @Autowired
    private NudgeRepository nudgeRepository;

    private String userId;
    private UUID uid;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.builder()
                .provider(SocialProvider.KAKAO)
                .providerId("kakao-" + UUID.randomUUID())
                .build()).getId();
        uid = UUID.fromString(userId);
        // 지난달 식비 10만원, 이번달 식비 30만원 → 평소의 3배라 이상 지출 넛지 대상
        expenseRepository.save(new Expense(uid, 100_000L, "식비", "지난달 점심", LocalDate.now().minusMonths(1).withDayOfMonth(2)));
        expenseRepository.save(new Expense(uid, 300_000L, "식비", "이번달 점심", LocalDate.now().withDayOfMonth(1)));
    }

    @AfterEach
    void tearDown() {
        userService.withdraw(userId);
    }

    private static OnboardingAnswerRequest answer(int step, String value) {
        OnboardingAnswerRequest request = new OnboardingAnswerRequest();
        ReflectionTestUtils.setField(request, "step", step);
        ReflectionTestUtils.setField(request, "value", value);
        return request;
    }

    private void completeOnboarding(String name, String region, long targetAmount) {
        onboardingService.submitAnswer(userId, answer(1, name));
        onboardingService.submitAnswer(userId, answer(2, "2000"));
        onboardingService.submitAnswer(userId, answer(3, "EMPLOYED"));
        onboardingService.submitAnswer(userId, answer(4, "300_380"));
        onboardingService.submitAnswer(userId, answer(5, region));
        onboardingService.submitAnswer(userId, answer(6, "HOMELESS"));
        onboardingService.submitAnswer(userId, answer(7, String.valueOf(targetAmount)));
    }

    @Test
    void 초기화_후에도_지출_건수와_금액이_그대로다() {
        completeOnboarding("지원", "SEOUL", 30_000_000L);

        assertThat(demoResetService.reset(userId).keptExpenses()).isEqualTo(2);

        assertThat(expenseRepository.findByUserId(uid))
                .hasSize(2)
                .extracting(Expense::getAmount)
                .containsExactlyInAnyOrder(100_000L, 300_000L);
    }

    @Test
    void 초기화_직후_넛지_조회는_오류_없이_동작하고_목표_넛지는_생기지_않는다() {
        completeOnboarding("지원", "SEOUL", 30_000_000L);
        nudgeService.getNudges(userId);
        demoResetService.reset(userId);

        NudgeListResponse response = nudgeService.getNudges(userId);

        // 목표가 지워져 목표 마감 넛지는 만들어지지 않는다
        assertThat(response.nudges()).noneSatisfy(nudge -> assertThat(nudge.getType()).isEqualTo("GOAL_DEADLINE"));
    }

    @Test
    void 초기화해도_이상_지출_넛지가_남고_같은_날_다시_만들어지지_않는다() {
        completeOnboarding("지원", "SEOUL", 30_000_000L);
        List<NudgeResponse> before = nudgeService.getNudges(userId).nudges();
        assertThat(before).anySatisfy(nudge -> assertThat(nudge.getType()).isEqualTo("UNUSUAL_SPENDING"));

        assertThat(demoResetService.reset(userId).keptNudges()).isEqualTo(before.size());

        // 넛지를 남기므로 남은 지출로 같은 날 같은 카테고리 넛지가 다시 생기지 않는다 (#253)
        List<NudgeResponse> afterReset = nudgeService.getNudges(userId).nudges();
        assertThat(afterReset).extracting(NudgeResponse::getNudgeId)
                .containsExactlyInAnyOrderElementsOf(before.stream().map(NudgeResponse::getNudgeId).toList());
        long afterFirst = afterReset.stream().filter(nudge -> "UNUSUAL_SPENDING".equals(nudge.getType())).count();
        assertThat(afterFirst).isEqualTo(1);

        // 여러 번 조회해도 같은 카테고리는 하루 한 번이라 쌓이지 않는다
        nudgeService.getNudges(userId);
        nudgeService.getNudges(userId);
        long afterRepeat = nudgeService.getNudges(userId).nudges().stream()
                .filter(nudge -> "UNUSUAL_SPENDING".equals(nudge.getType())).count();
        assertThat(afterRepeat).isEqualTo(1);
    }

    @Test
    void 지워진_목표의_넛지가_남은_채로_초기화_온보딩_목표_생성까지_오류가_없다() {
        completeOnboarding("지원", "SEOUL", 30_000_000L);
        // 지워질 목표를 가리키는 넛지 (넛지는 목표를 외래키가 아닌 메시지 문구로만 담는다)
        nudgeRepository.save(Nudge.builder().userId(userId).type("GOAL_DEADLINE")
                .message("목표 '이전 목표' 마감까지 D-10일 남았습니다.").build());
        nudgeService.getNudges(userId);

        DemoResetResult result = demoResetService.reset(userId);
        assertThat(result.deleted().goals()).isPositive();
        assertThat(result.keptNudges()).isPositive();

        completeOnboarding("민수", "BUSAN", 5_000_000L);
        assertThat(goalRepository.findActiveByUserId(uid)).get()
                .satisfies(goal -> assertThat(goal.getTargetAmount()).isEqualTo(5_000_000L));

        NudgeListResponse response = nudgeService.getNudges(userId);
        assertThat(response.nudges()).anySatisfy(nudge -> assertThat(nudge.getMessage()).contains("이전 목표"));
    }

    @Test
    void 초기화_후_재온보딩하면_새_목표로_시뮬레이션이_동작한다() {
        completeOnboarding("지원", "SEOUL", 30_000_000L);
        demoResetService.reset(userId);
        completeOnboarding("민수", "BUSAN", 50_000_000L);

        Goal goal = goalRepository.findActiveByUserId(uid).orElseThrow();
        SimulationResponse response = simulationService.runSimulation(uid,
                new SimulationRequest(goal.getGoalId(), 500_000L, null, null));

        // 시뮬레이션은 지출을 쓰지 않으므로 남은 지출과 무관하게 새 목표 금액으로 계산된다
        assertThat(goal.getTargetAmount()).isEqualTo(50_000_000L);
        assertThat(response).isNotNull();
        assertThat(simulationService.getSimulations(uid)).hasSize(1);
    }

    @Test
    void 초기화_후_지출_조회와_통계가_남은_지출로_동작한다() {
        completeOnboarding("지원", "SEOUL", 30_000_000L);
        demoResetService.reset(userId);

        assertThatCode(() -> expenseRepository.sumByCategory(uid,
                LocalDate.now().minusMonths(2), LocalDate.now())).doesNotThrowAnyException();
        assertThat(expenseRepository.sumByCategory(uid, LocalDate.now().withDayOfMonth(1), LocalDate.now()))
                .isNotEmpty();
    }
}
