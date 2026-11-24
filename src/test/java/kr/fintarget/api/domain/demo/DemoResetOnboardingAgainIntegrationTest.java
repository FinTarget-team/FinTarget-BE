package kr.fintarget.api.domain.demo;

import kr.fintarget.api.domain.goal.entity.Goal;
import kr.fintarget.api.domain.goal.repository.GoalRepository;
import kr.fintarget.api.domain.onboarding.dto.OnboardingAnswerRequest;
import kr.fintarget.api.domain.onboarding.dto.OnboardingStepResponse;
import kr.fintarget.api.domain.onboarding.service.OnboardingService;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.dto.UserProfileResponse;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import kr.fintarget.api.domain.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 초기화한 계정으로 온보딩을 1단계부터 끝까지 다시 진행할 수 있고, 목표가 다시 생성되는지 확인한다 (#239).
 */
@SpringBootTest
class DemoResetOnboardingAgainIntegrationTest {

    @Autowired
    private DemoResetService demoResetService;
    @Autowired
    private OnboardingService onboardingService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private GoalRepository goalRepository;

    private String userId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.builder()
                .provider(SocialProvider.KAKAO)
                .providerId("kakao-" + UUID.randomUUID())
                .build()).getId();
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

    private OnboardingStepResponse completeOnboarding(String name, String region, long targetAmount) {
        onboardingService.submitAnswer(userId, answer(1, name));
        onboardingService.submitAnswer(userId, answer(2, "2000"));
        onboardingService.submitAnswer(userId, answer(3, "EMPLOYED"));
        onboardingService.submitAnswer(userId, answer(4, "300_380"));
        onboardingService.submitAnswer(userId, answer(5, region));
        onboardingService.submitAnswer(userId, answer(6, "HOMELESS"));
        return onboardingService.submitAnswer(userId, answer(7, String.valueOf(targetAmount)));
    }

    private List<Goal> activeGoals() {
        return goalRepository.findAll().stream()
                .filter(goal -> goal.getUserId().equals(UUID.fromString(userId)))
                .filter(goal -> goal.getStatus() == Goal.GoalStatus.ACTIVE)
                .toList();
    }

    @Test
    void 초기화_후_온보딩을_처음부터_다시_완료할_수_있다() {
        completeOnboarding("지원", "SEOUL", 30_000_000L);
        demoResetService.reset(userId);

        OnboardingStepResponse last = completeOnboarding("민수", "BUSAN", 50_000_000L);

        assertThat(last.isComplete()).isTrue();
        UserProfileResponse profile = userService.getProfile(userId);
        assertThat(profile.isOnboardingCompleted()).isTrue();
        assertThat(profile.getName()).isEqualTo("민수");
        assertThat(profile.getRegion()).isEqualTo("BUSAN");
    }

    @Test
    void 초기화_후_다시_온보딩하면_목표가_새로_하나_생긴다() {
        completeOnboarding("지원", "SEOUL", 30_000_000L);
        demoResetService.reset(userId);
        assertThat(activeGoals()).isEmpty();

        completeOnboarding("민수", "BUSAN", 50_000_000L);

        assertThat(activeGoals()).singleElement()
                .satisfies(goal -> assertThat(goal.getTargetAmount()).isEqualTo(50_000_000L));
    }

    @Test
    void 초기화_직후에는_2단계를_먼저_제출할_수_없다() {
        completeOnboarding("지원", "SEOUL", 30_000_000L);
        demoResetService.reset(userId);

        assertThatThrownBy(() -> onboardingService.submitAnswer(userId, answer(2, "2000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이전 온보딩 단계를 먼저 완료해주세요");
    }
}
