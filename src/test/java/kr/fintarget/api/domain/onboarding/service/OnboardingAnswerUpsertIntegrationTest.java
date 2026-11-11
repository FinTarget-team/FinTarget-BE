package kr.fintarget.api.domain.onboarding.service;

import kr.fintarget.api.domain.onboarding.dto.OnboardingAnswerRequest;
import kr.fintarget.api.domain.onboarding.entity.OnboardingAnswer;
import kr.fintarget.api.domain.onboarding.repository.OnboardingAnswerRepository;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import kr.fintarget.api.domain.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 DB(H2)에서 같은 단계를 여러 번 제출해도 답변이 1건만 남고, (user_id, step) 유니크 제약이 걸리는지 확인한다 (#195).
 */
@SpringBootTest
class OnboardingAnswerUpsertIntegrationTest {

    @Autowired
    private OnboardingService onboardingService;
    @Autowired
    private OnboardingAnswerRepository onboardingAnswerRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;

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

    @Test
    void 같은_단계를_여러_번_제출해도_답변은_1건이고_마지막_값으로_갱신된다() {
        onboardingService.submitAnswer(userId, answer(1, "홍길동"));
        onboardingService.submitAnswer(userId, answer(1, "김철수"));
        onboardingService.submitAnswer(userId, answer(1, "이영희"));

        List<OnboardingAnswer> answers = onboardingAnswerRepository.findByUserIdOrderByStepAsc(UUID.fromString(userId));
        assertThat(answers).hasSize(1);
        assertThat(answers.get(0).getValue()).isEqualTo("이영희");
        assertThat(userRepository.findById(userId).orElseThrow().getName()).isEqualTo("이영희");
    }

    @Test
    void 단계가_다르면_각각_저장된다() {
        onboardingService.submitAnswer(userId, answer(1, "홍길동"));
        onboardingService.submitAnswer(userId, answer(2, "1999"));

        assertThat(onboardingAnswerRepository.findByUserIdOrderByStepAsc(UUID.fromString(userId)))
                .extracting(OnboardingAnswer::getStep).containsExactly(1, 2);
    }

    @Test
    void 같은_사용자와_단계의_답변을_직접_두_번_넣으면_유니크_제약으로_막힌다() {
        UUID uid = UUID.fromString(userId);
        onboardingAnswerRepository.saveAndFlush(new OnboardingAnswer(uid, 1, "홍길동"));

        assertThatThrownBy(() -> onboardingAnswerRepository.saveAndFlush(new OnboardingAnswer(uid, 1, "김철수")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
