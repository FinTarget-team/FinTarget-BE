package kr.fintarget.api.domain.onboarding.service;

import kr.fintarget.api.domain.onboarding.dto.OnboardingAnswerRequest;
import kr.fintarget.api.domain.onboarding.dto.OnboardingStepResponse;
import kr.fintarget.api.domain.onboarding.entity.OnboardingAnswer;
import kr.fintarget.api.domain.onboarding.repository.OnboardingAnswerRepository;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OnboardingAnswerRepository onboardingAnswerRepository;

    @InjectMocks
    private OnboardingService onboardingService;

    private OnboardingAnswerRequest answerRequest(int step, String value) {
        OnboardingAnswerRequest request = new OnboardingAnswerRequest();
        ReflectionTestUtils.setField(request, "step", step);
        ReflectionTestUtils.setField(request, "value", value);
        return request;
    }

    @Test
    void step4_조회시_CHIP_타입과_전국_17개_시도_옵션을_반환한다() {
        OnboardingStepResponse response = onboardingService.getStep(4);

        assertThat(response.getStep()).isEqualTo(4);
        assertThat(response.getAnswerType()).isEqualTo("CHIP");
        assertThat(response.getOptions()).hasSize(17);
        assertThat(response.getOptions())
                .extracting(OnboardingStepResponse.OptionDto::getValue)
                .containsExactly(
                        "SEOUL", "BUSAN", "DAEGU", "INCHEON", "GWANGJU", "DAEJEON", "ULSAN",
                        "SEJONG", "GYEONGGI", "GANGWON", "CHUNGBUK", "CHUNGNAM", "JEONBUK",
                        "JEONNAM", "GYEONGBUK", "GYEONGNAM", "JEJU"
                );
        assertThat(response.getOptions())
                .extracting(OnboardingStepResponse.OptionDto::getLabel)
                .contains("서울", "제주");
    }

    @Test
    void step4_답변_제출시_유효한_지역코드면_유저_region이_갱신되고_5단계_질문을_반환한다() {
        String userId = UUID.randomUUID().toString();
        User user = User.builder().id(userId).provider("kakao").providerId("provider-1").build();

        when(onboardingAnswerRepository.existsByUserIdAndStep(UUID.fromString(userId), 3)).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        OnboardingStepResponse response = onboardingService.submitAnswer(userId, answerRequest(4, "SEOUL"));

        assertThat(response.getStep()).isEqualTo(5);
        assertThat(response.getAnswerType()).isEqualTo("NUMBER");
        assertThat(user.getRegion()).isEqualTo("SEOUL");
        verify(onboardingAnswerRepository).save(any(OnboardingAnswer.class));
        verify(userRepository).save(user);
    }

    @Test
    void step4_답변_제출시_유효하지_않은_지역코드면_400_예외가_발생한다() {
        String userId = UUID.randomUUID().toString();

        when(onboardingAnswerRepository.existsByUserIdAndStep(UUID.fromString(userId), 3)).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(
                User.builder().id(userId).provider("kakao").providerId("provider-1").build()
        ));

        assertThatThrownBy(() -> onboardingService.submitAnswer(userId, answerRequest(4, "ATLANTIS")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 지역 코드입니다");

        verify(userRepository, never()).save(any());
    }

    @Test
    void step4_답변_제출시_이전_단계를_완료하지_않았으면_예외가_발생한다() {
        String userId = UUID.randomUUID().toString();

        when(onboardingAnswerRepository.existsByUserIdAndStep(UUID.fromString(userId), 3)).thenReturn(false);

        assertThatThrownBy(() -> onboardingService.submitAnswer(userId, answerRequest(4, "SEOUL")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이전 온보딩 단계를 먼저 완료해주세요");

        verify(onboardingAnswerRepository, never()).save(any());
        verify(userRepository, never()).findById(any());
    }
}
