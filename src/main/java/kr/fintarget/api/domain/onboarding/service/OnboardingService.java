package kr.fintarget.api.domain.onboarding.service;

import kr.fintarget.api.domain.onboarding.dto.OnboardingAnswerRequest;
import kr.fintarget.api.domain.onboarding.dto.OnboardingStepResponse;
import kr.fintarget.api.domain.onboarding.entity.OnboardingAnswer;
import kr.fintarget.api.domain.onboarding.repository.OnboardingAnswerRepository;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final UserRepository userRepository;
    private final OnboardingAnswerRepository onboardingAnswerRepository;
    private static final int TOTAL_STEPS = 5;

    private static final Map<String, Integer> AGE_CODE_MAP = Map.of(
            "YOUTH", 25,
            "MIDDLE", 40
    );

    private static final Map<String, Long> INCOME_CODE_MAP = Map.of(
            "UNDER_2M", 1_500_000L,
            "200_300", 2_500_000L,
            "OVER_3M", 3_500_000L
    );

    public OnboardingStepResponse getStep(int step) {
        return switch (step) {
            case 1 -> OnboardingStepResponse.builder()
                    .step(1).totalSteps(TOTAL_STEPS).progress(0.2)
                    .question("나이를 알면 받을 수 있는 정책이 달라져요! 어느 시기를 보내고 계신가요?")
                    .answerType("CHIP")
                    .options(List.of(
                            OnboardingStepResponse.OptionDto.builder().label("청년 (~39세)").value("YOUTH").build(),
                            OnboardingStepResponse.OptionDto.builder().label("중장년 (40대~)").value("MIDDLE").build()
                    ))
                    .isComplete(false).build();
            case 2 -> OnboardingStepResponse.builder()
                    .step(2).totalSteps(TOTAL_STEPS).progress(0.4)
                    .question("고용 형태에 따라 신청 가능한 정책이 다르답니다. 현재 어떤 일을 하고 계신가요?")
                    .answerType("CHIP")
                    .options(List.of(
                            OnboardingStepResponse.OptionDto.builder().label("직장인").value("EMPLOYED").build(),
                            OnboardingStepResponse.OptionDto.builder().label("구직 중").value("UNEMPLOYED").build(),
                            OnboardingStepResponse.OptionDto.builder().label("학생").value("STUDENT").build(),
                            OnboardingStepResponse.OptionDto.builder().label("자영업자").value("SELF_EMPLOYED").build()
                    ))
                    .isComplete(false).build();
            case 3 -> OnboardingStepResponse.builder()
                    .step(3).totalSteps(TOTAL_STEPS).progress(0.6)
                    .question("소득 수준에 맞는 청년 정책을 찾아드릴게요! 월 소득이 어느 정도인가요?")
                    .answerType("CHIP")
                    .options(List.of(
                            OnboardingStepResponse.OptionDto.builder().label("200만원 미만").value("UNDER_2M").build(),
                            OnboardingStepResponse.OptionDto.builder().label("200~300만원").value("200_300").build(),
                            OnboardingStepResponse.OptionDto.builder().label("300만원 이상").value("OVER_3M").build()
                    ))
                    .isComplete(false).build();
            case 4 -> OnboardingStepResponse.builder()
                    .step(4).totalSteps(TOTAL_STEPS).progress(0.8)
                    .question("거주 지역별로 받을 수 있는 지역 정책도 있어요. 현재 어디에 살고 계신가요?")
                    .answerType("TEXT")
                    .options(List.of())
                    .isComplete(false).build();
            case 5 -> OnboardingStepResponse.builder()
                    .step(5).totalSteps(TOTAL_STEPS).progress(1.0)
                    .question("목표 금액을 알면 달성일을 바로 계산해드릴게요. 목표 자산이 얼마인가요?")
                    .answerType("NUMBER")
                    .options(List.of())
                    .isComplete(false).build();
            default -> throw new IllegalArgumentException("잘못된 단계입니다.");
        };
    }

    @Transactional
    public OnboardingStepResponse submitAnswer(String userId, OnboardingAnswerRequest request) {
        // 답변 DB 저장
        OnboardingAnswer answer = new OnboardingAnswer(
                UUID.fromString(userId),
                request.getStep(),
                request.getValue()
        );

        onboardingAnswerRepository.save(answer);

        // step별 답변을 User 엔티티에 반영 (step1: age, step2: employmentType, step3: income)
        applyAnswerToUser(userId, request.getStep(), request.getValue());

        // 마지막 단계면 완료 처리
        if (request.getStep() == TOTAL_STEPS) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
            user.completeOnboarding();
            userRepository.save(user);

            return OnboardingStepResponse.builder()
                    .step(TOTAL_STEPS).totalSteps(TOTAL_STEPS).progress(1.0)
                    .question("완료! 맞춤 정책과 시뮬레이션을 준비할게요.")
                    .answerType("NONE").options(List.of())
                    .isComplete(true).build();
        }

        return getStep(request.getStep() + 1);
    }

    private void applyAnswerToUser(String userId, int step, String value) {
        if (step != 1 && step != 2 && step != 3) return;

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        switch (step) {
            case 1 -> Optional.ofNullable(AGE_CODE_MAP.get(value)).ifPresent(user::updateAge);
            case 2 -> user.updateEmploymentType(value);
            case 3 -> Optional.ofNullable(INCOME_CODE_MAP.get(value)).ifPresent(user::updateIncome);
        }

        userRepository.save(user);
    }
}