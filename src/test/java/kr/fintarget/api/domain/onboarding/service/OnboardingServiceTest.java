package kr.fintarget.api.domain.onboarding.service;

import kr.fintarget.api.domain.goal.service.GoalService;
import kr.fintarget.api.domain.onboarding.dto.OnboardingAnswerRequest;
import kr.fintarget.api.domain.onboarding.dto.OnboardingStepResponse;
import kr.fintarget.api.domain.onboarding.entity.OnboardingAnswer;
import kr.fintarget.api.domain.onboarding.repository.OnboardingAnswerRepository;
import kr.fintarget.api.domain.user.code.IncomeBracket;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static kr.fintarget.api.domain.onboarding.service.OnboardingService.STEP_AGE;
import static kr.fintarget.api.domain.onboarding.service.OnboardingService.STEP_EMPLOYMENT;
import static kr.fintarget.api.domain.onboarding.service.OnboardingService.STEP_GOAL;
import static kr.fintarget.api.domain.onboarding.service.OnboardingService.STEP_INCOME;
import static kr.fintarget.api.domain.onboarding.service.OnboardingService.STEP_NAME;
import static kr.fintarget.api.domain.onboarding.service.OnboardingService.STEP_REGION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OnboardingAnswerRepository onboardingAnswerRepository;

    @Mock
    private GoalService goalService;

    @InjectMocks
    private OnboardingService onboardingService;

    private OnboardingAnswerRequest answerRequest(int step, String value) {
        OnboardingAnswerRequest request = new OnboardingAnswerRequest();
        ReflectionTestUtils.setField(request, "step", step);
        ReflectionTestUtils.setField(request, "value", value);
        return request;
    }

    @Test
    void step1_조회시_TEXT_타입과_빈_옵션을_반환한다() {
        OnboardingStepResponse response = onboardingService.getStep(STEP_NAME);

        assertThat(response.getStep()).isEqualTo(STEP_NAME);
        assertThat(response.getAnswerType()).isEqualTo("TEXT");
        assertThat(response.getOptions()).isEmpty();
    }

    @Test
    void step1_답변_제출시_정상_이름이면_유저_name이_갱신되고_2단계_질문을_반환한다() {
        String userId = UUID.randomUUID().toString();
        User user = User.builder().id(userId).provider("kakao").providerId("provider-1").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        OnboardingStepResponse response = onboardingService.submitAnswer(userId, answerRequest(STEP_NAME, "홍길동"));

        assertThat(response.getStep()).isEqualTo(STEP_AGE);
        assertThat(response.getAnswerType()).isEqualTo("CHIP");
        assertThat(user.getName()).isEqualTo("홍길동");
        verify(onboardingAnswerRepository).save(any(OnboardingAnswer.class));
        verify(userRepository).save(user);
    }

    @Test
    void step1_답변_제출시_값이_빈_문자열이면_400_예외가_발생한다() {
        String userId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> onboardingService.submitAnswer(userId, answerRequest(STEP_NAME, "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("답변 값을 입력해주세요");

        verify(onboardingAnswerRepository, never()).save(any());
    }

    @Test
    void step1_답변_제출시_값이_공백만이면_400_예외가_발생한다() {
        String userId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> onboardingService.submitAnswer(userId, answerRequest(STEP_NAME, "   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("답변 값을 입력해주세요");

        verify(onboardingAnswerRepository, never()).save(any());
    }

    @Test
    void step1_답변_제출시_값이_전각_공백만이면_400_예외가_발생한다() {
        String userId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> onboardingService.submitAnswer(userId, answerRequest(STEP_NAME, "　　")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("답변 값을 입력해주세요");

        verify(onboardingAnswerRepository, never()).save(any());
    }

    @Test
    void step1_답변_제출시_이름이_20자면_정상_저장된다() {
        String userId = UUID.randomUUID().toString();
        User user = User.builder().id(userId).provider("kakao").providerId("provider-1").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        String name20 = "가".repeat(20);
        onboardingService.submitAnswer(userId, answerRequest(STEP_NAME, name20));

        assertThat(user.getName()).isEqualTo(name20);
    }

    @Test
    void step1_답변_제출시_이름이_21자면_400_예외가_발생한다() {
        String userId = UUID.randomUUID().toString();
        User user = User.builder().id(userId).provider("kakao").providerId("provider-1").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        String name21 = "가".repeat(21);

        assertThatThrownBy(() -> onboardingService.submitAnswer(userId, answerRequest(STEP_NAME, name21)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이름은 20자 이하여야 합니다");

        assertThat(user.getName()).isNull();
    }

    @Test
    void step1_답변_제출시_앞뒤_공백은_제거되어_유저_name에_저장되고_답변에는_원문이_저장된다() {
        String userId = UUID.randomUUID().toString();
        User user = User.builder().id(userId).provider("kakao").providerId("provider-1").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        onboardingService.submitAnswer(userId, answerRequest(STEP_NAME, "  홍길동  "));

        assertThat(user.getName()).isEqualTo("홍길동");

        ArgumentCaptor<OnboardingAnswer> captor = ArgumentCaptor.forClass(OnboardingAnswer.class);
        verify(onboardingAnswerRepository).save(captor.capture());
        assertThat(captor.getValue().getValue()).isEqualTo("  홍길동  ");
    }

    @Test
    void step2_답변_제출시_1단계를_완료하지_않았으면_예외가_발생한다() {
        String userId = UUID.randomUUID().toString();

        when(onboardingAnswerRepository.existsByUserIdAndStep(UUID.fromString(userId), STEP_NAME)).thenReturn(false);

        assertThatThrownBy(() -> onboardingService.submitAnswer(userId, answerRequest(STEP_AGE, "YOUTH")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이전 온보딩 단계를 먼저 완료해주세요");

        verify(onboardingAnswerRepository, never()).save(any());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void step5_조회시_CHIP_타입과_전국_17개_시도_옵션을_반환한다() {
        OnboardingStepResponse response = onboardingService.getStep(STEP_REGION);

        assertThat(response.getStep()).isEqualTo(STEP_REGION);
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
    void step5_답변_제출시_유효한_지역코드면_유저_region이_갱신되고_6단계_질문을_반환한다() {
        String userId = UUID.randomUUID().toString();
        User user = User.builder().id(userId).provider("kakao").providerId("provider-1").build();

        when(onboardingAnswerRepository.existsByUserIdAndStep(UUID.fromString(userId), STEP_INCOME)).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        OnboardingStepResponse response = onboardingService.submitAnswer(userId, answerRequest(STEP_REGION, "SEOUL"));

        assertThat(response.getStep()).isEqualTo(STEP_GOAL);
        assertThat(response.getAnswerType()).isEqualTo("NUMBER");
        assertThat(user.getRegion()).isEqualTo("SEOUL");
        verify(onboardingAnswerRepository).save(any(OnboardingAnswer.class));
        verify(userRepository).save(user);
    }

    @Test
    void step5_답변_제출시_유효하지_않은_지역코드면_400_예외가_발생한다() {
        String userId = UUID.randomUUID().toString();

        when(onboardingAnswerRepository.existsByUserIdAndStep(UUID.fromString(userId), STEP_INCOME)).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(
                User.builder().id(userId).provider("kakao").providerId("provider-1").build()
        ));

        assertThatThrownBy(() -> onboardingService.submitAnswer(userId, answerRequest(STEP_REGION, "ATLANTIS")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 지역 코드입니다");

        verify(userRepository, never()).save(any());
    }

    @Test
    void step3_답변_제출시_유효한_고용형태_코드면_유저_employmentType이_갱신되고_4단계_질문을_반환한다() {
        String userId = UUID.randomUUID().toString();
        User user = User.builder().id(userId).provider("kakao").providerId("provider-1").build();

        when(onboardingAnswerRepository.existsByUserIdAndStep(UUID.fromString(userId), STEP_AGE)).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        OnboardingStepResponse response = onboardingService.submitAnswer(userId, answerRequest(STEP_EMPLOYMENT, "STUDENT"));

        assertThat(response.getStep()).isEqualTo(STEP_INCOME);
        assertThat(user.getEmploymentType()).isEqualTo("STUDENT");
        verify(userRepository).save(user);
    }

    @Test
    void step3_답변_제출시_유효하지_않은_고용형태_코드면_400_예외가_발생한다() {
        String userId = UUID.randomUUID().toString();

        when(onboardingAnswerRepository.existsByUserIdAndStep(UUID.fromString(userId), STEP_AGE)).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(
                User.builder().id(userId).provider("kakao").providerId("provider-1").build()
        ));

        assertThatThrownBy(() -> onboardingService.submitAnswer(userId, answerRequest(STEP_EMPLOYMENT, "FREELANCER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 고용 형태 코드입니다");

        verify(userRepository, never()).save(any());
    }

    @Test
    void step4_조회시_세전_월소득_질문과_6개_소득구간_옵션을_반환한다() {
        OnboardingStepResponse response = onboardingService.getStep(STEP_INCOME);

        assertThat(response.getStep()).isEqualTo(STEP_INCOME);
        assertThat(response.getAnswerType()).isEqualTo("CHIP");
        assertThat(response.getQuestion()).contains("세전 월 소득");
        assertThat(response.getOptions())
                .extracting(OnboardingStepResponse.OptionDto::getValue)
                .containsExactly(Arrays.stream(IncomeBracket.values()).map(IncomeBracket::getCode).toArray(String[]::new));
        assertThat(response.getOptions())
                .extracting(OnboardingStepResponse.OptionDto::getLabel)
                .containsExactly("150만원 미만", "150~260만원", "260~300만원", "300~380만원", "380~410만원", "410만원 이상");
    }

    @Test
    void step4_답변_제출시_유효한_소득코드면_유저_income이_대표값으로_갱신되고_5단계_질문을_반환한다() {
        String userId = UUID.randomUUID().toString();
        User user = User.builder().id(userId).provider("kakao").providerId("provider-1").build();

        when(onboardingAnswerRepository.existsByUserIdAndStep(UUID.fromString(userId), STEP_EMPLOYMENT)).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        OnboardingStepResponse response = onboardingService.submitAnswer(userId, answerRequest(STEP_INCOME, "260_300"));

        assertThat(response.getStep()).isEqualTo(STEP_REGION);
        assertThat(user.getIncome()).isEqualTo(2_800_000L);
        verify(userRepository).save(user);
    }

    @Test
    void step4_답변_제출시_구간_개편_전_소득코드면_400_예외가_발생한다() {
        String userId = UUID.randomUUID().toString();

        when(onboardingAnswerRepository.existsByUserIdAndStep(UUID.fromString(userId), STEP_EMPLOYMENT)).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(
                User.builder().id(userId).provider("kakao").providerId("provider-1").build()
        ));

        assertThatThrownBy(() -> onboardingService.submitAnswer(userId, answerRequest(STEP_INCOME, "UNDER_2M")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 소득 코드입니다");

        verify(userRepository, never()).save(any());
    }

    @Test
    void step5_답변_제출시_이전_단계를_완료하지_않았으면_예외가_발생한다() {
        String userId = UUID.randomUUID().toString();

        when(onboardingAnswerRepository.existsByUserIdAndStep(UUID.fromString(userId), STEP_INCOME)).thenReturn(false);

        assertThatThrownBy(() -> onboardingService.submitAnswer(userId, answerRequest(STEP_REGION, "SEOUL")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이전 온보딩 단계를 먼저 완료해주세요");

        verify(onboardingAnswerRepository, never()).save(any());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void step6_답변_제출시_목표가_생성되고_온보딩이_완료된다() {
        String userId = UUID.randomUUID().toString();
        User user = User.builder().id(userId).provider("kakao").providerId("provider-1").build();

        when(onboardingAnswerRepository.existsByUserIdAndStep(UUID.fromString(userId), STEP_REGION)).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        OnboardingStepResponse response = onboardingService.submitAnswer(userId, answerRequest(STEP_GOAL, " 30000000 "));

        assertThat(response.isComplete()).isTrue();
        verify(goalService).upsertFromOnboarding(UUID.fromString(userId), 30_000_000L);
        verify(userRepository).save(user);
    }

    @Test
    void step6_답변이_숫자가_아니면_400_예외가_발생하고_목표를_만들지_않는다() {
        String userId = UUID.randomUUID().toString();

        when(onboardingAnswerRepository.existsByUserIdAndStep(UUID.fromString(userId), STEP_REGION)).thenReturn(true);

        assertThatThrownBy(() -> onboardingService.submitAnswer(userId, answerRequest(STEP_GOAL, "3천만원")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("목표 금액은 숫자로 입력해주세요");

        verify(goalService, never()).upsertFromOnboarding(any(), anyLong());
        verify(userRepository, never()).save(any());
    }

    @Test
    void step6_답변이_Long_범위를_넘는_자릿수면_상한_초과값으로_목표_검증에_넘긴다() {
        String userId = UUID.randomUUID().toString();

        when(onboardingAnswerRepository.existsByUserIdAndStep(UUID.fromString(userId), STEP_REGION)).thenReturn(true);
        doThrow(new IllegalArgumentException("목표 금액은 50억 원 이하로 입력해주세요"))
                .when(goalService).upsertFromOnboarding(UUID.fromString(userId), Long.MAX_VALUE);

        assertThatThrownBy(() -> onboardingService.submitAnswer(userId, answerRequest(STEP_GOAL, "99999999999999999999")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("목표 금액은 50억 원 이하로 입력해주세요");

        verify(userRepository, never()).save(any());
    }
}
