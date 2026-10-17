package kr.fintarget.api.domain.onboarding.service;

import kr.fintarget.api.domain.goal.service.GoalService;
import kr.fintarget.api.domain.onboarding.dto.OnboardingAnswerRequest;
import kr.fintarget.api.domain.onboarding.dto.OnboardingStepResponse;
import kr.fintarget.api.domain.onboarding.entity.OnboardingAnswer;
import kr.fintarget.api.domain.onboarding.repository.OnboardingAnswerRepository;
import kr.fintarget.api.domain.user.code.AgeGroup;
import kr.fintarget.api.domain.user.code.EmploymentType;
import kr.fintarget.api.domain.user.code.IncomeBracket;
import kr.fintarget.api.domain.user.code.Region;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import kr.fintarget.api.domain.user.validation.NameValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final UserRepository userRepository;
    private final OnboardingAnswerRepository onboardingAnswerRepository;
    private final GoalService goalService;
    static final int STEP_NAME = 1;
    static final int STEP_AGE = 2;
    static final int STEP_EMPLOYMENT = 3;
    static final int STEP_INCOME = 4;
    static final int STEP_REGION = 5;
    static final int STEP_GOAL = 6;
    static final int TOTAL_STEPS = STEP_GOAL;

    private static final List<OnboardingStepResponse.OptionDto> REGION_OPTIONS = Arrays.stream(Region.values())
            .map(region -> OnboardingStepResponse.OptionDto.builder().label(region.getLabel()).value(region.getCode()).build())
            .collect(Collectors.toList());

    public OnboardingStepResponse getStep(int step) {
        return switch (step) {
            case STEP_NAME -> OnboardingStepResponse.builder()
                    .step(STEP_NAME).totalSteps(TOTAL_STEPS).progress((double) STEP_NAME / TOTAL_STEPS)
                    .question("사용할 닉네임을 입력해 주세요. (최대 20자)")
                    .answerType("TEXT")
                    .options(List.of())
                    .isComplete(false).build();
            case STEP_AGE -> OnboardingStepResponse.builder()
                    .step(STEP_AGE).totalSteps(TOTAL_STEPS).progress((double) STEP_AGE / TOTAL_STEPS)
                    .question("나이를 알면 받을 수 있는 정책이 달라져요. 어느 시기를 보내고 계신가요?")
                    .answerType("CHIP")
                    .options(List.of(
                            OnboardingStepResponse.OptionDto.builder().label("청년 (~39세)").value("YOUTH").build(),
                            OnboardingStepResponse.OptionDto.builder().label("중장년 (40대~)").value("MIDDLE").build()
                    ))
                    .isComplete(false).build();
            case STEP_EMPLOYMENT -> OnboardingStepResponse.builder()
                    .step(STEP_EMPLOYMENT).totalSteps(TOTAL_STEPS).progress((double) STEP_EMPLOYMENT / TOTAL_STEPS)
                    .question("고용 형태에 따라 신청 가능한 정책이 달라져요. 현재 어떤 일을 하고 계신가요?")
                    .answerType("CHIP")
                    .options(List.of(
                            OnboardingStepResponse.OptionDto.builder().label("직장인").value("EMPLOYED").build(),
                            OnboardingStepResponse.OptionDto.builder().label("구직 중").value("UNEMPLOYED").build(),
                            OnboardingStepResponse.OptionDto.builder().label("학생").value("STUDENT").build(),
                            OnboardingStepResponse.OptionDto.builder().label("자영업자").value("SELF_EMPLOYED").build()
                    ))
                    .isComplete(false).build();
            case STEP_INCOME -> OnboardingStepResponse.builder()
                    .step(STEP_INCOME).totalSteps(TOTAL_STEPS).progress((double) STEP_INCOME / TOTAL_STEPS)
                    .question("소득 수준에 맞는 청년 정책을 찾아드릴게요. 월 소득이 어느 정도인가요?")
                    .answerType("CHIP")
                    .options(List.of(
                            OnboardingStepResponse.OptionDto.builder().label("200만원 미만").value("UNDER_2M").build(),
                            OnboardingStepResponse.OptionDto.builder().label("200~300만원").value("200_300").build(),
                            OnboardingStepResponse.OptionDto.builder().label("300만원 이상").value("OVER_3M").build()
                    ))
                    .isComplete(false).build();
            case STEP_REGION -> OnboardingStepResponse.builder()
                    .step(STEP_REGION).totalSteps(TOTAL_STEPS).progress((double) STEP_REGION / TOTAL_STEPS)
                    .question("거주 지역에 따라 받을 수 있는 정책이 달라져요. 현재 어느 지역에 살고 계신가요?")
                    .answerType("CHIP")
                    .options(REGION_OPTIONS)
                    .isComplete(false).build();
            case STEP_GOAL -> OnboardingStepResponse.builder()
                    .step(STEP_GOAL).totalSteps(TOTAL_STEPS).progress((double) STEP_GOAL / TOTAL_STEPS)
                    .question("목표 금액을 알면 달성일을 바로 계산해드릴게요. 목표 자산이 얼마인가요?")
                    .answerType("NUMBER")
                    .options(List.of())
                    .isComplete(false).build();
            default -> throw new IllegalArgumentException("잘못된 단계입니다.");
        };
    }

    @Transactional
    public OnboardingStepResponse submitAnswer(String userId, OnboardingAnswerRequest request) {
        validateAnswerRequest(request);
        validatePreviousStepCompleted(userId, request.getStep());

        // 답변 DB 저장
        OnboardingAnswer answer = new OnboardingAnswer(
                UUID.fromString(userId),
                request.getStep(),
                request.getValue()
        );

        onboardingAnswerRepository.save(answer);

        // step별 답변을 User 엔티티에 반영 (STEP_NAME: name, STEP_AGE: age, STEP_EMPLOYMENT: employmentType, STEP_INCOME: income, STEP_REGION: region)
        applyAnswerToUser(userId, request.getStep(), request.getValue());

        // 마지막 단계면 목표 생성 후 완료 처리
        if (request.getStep() == STEP_GOAL) {
            goalService.upsertFromOnboarding(UUID.fromString(userId), resolveTargetAmount(request.getValue()));

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
            user.completeOnboarding();
            userRepository.save(user);

            return OnboardingStepResponse.builder()
                    .step(STEP_GOAL).totalSteps(TOTAL_STEPS).progress(1.0)
                    .question("완료! 맞춤 정책과 시뮬레이션을 준비할게요.")
                    .answerType("NONE").options(List.of())
                    .isComplete(true).build();
        }

        return getStep(request.getStep() + 1);
    }

    private void validateAnswerRequest(OnboardingAnswerRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("요청 값이 없습니다");
        }
        if (request.getValue() == null || request.getValue().isBlank()) {
            throw new IllegalArgumentException("답변 값을 입력해주세요");
        }
        if (request.getStep() < 1 || request.getStep() > TOTAL_STEPS) {
            throw new IllegalArgumentException("유효하지 않은 온보딩 단계입니다");
        }
    }

    private void validatePreviousStepCompleted(String userId, int step) {
        if (step == STEP_NAME) return;

        boolean previousStepCompleted = onboardingAnswerRepository
                .existsByUserIdAndStep(UUID.fromString(userId), step - 1);
        if (!previousStepCompleted) {
            throw new IllegalArgumentException("이전 온보딩 단계를 먼저 완료해주세요");
        }
    }

    private void applyAnswerToUser(String userId, int step, String value) {
        if (step != STEP_NAME && step != STEP_AGE && step != STEP_EMPLOYMENT
                && step != STEP_INCOME && step != STEP_REGION) return;

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        switch (step) {
            case STEP_NAME -> user.updateName(NameValidator.validate(value));
            case STEP_AGE -> user.updateAge(resolveAge(value));
            case STEP_EMPLOYMENT -> user.updateEmploymentType(resolveEmploymentType(value));
            case STEP_INCOME -> user.updateIncome(resolveIncome(value));
            case STEP_REGION -> user.updateRegion(resolveRegion(value));
        }

        userRepository.save(user);
    }

    private Integer resolveAge(String value) {
        AgeGroup ageGroup = AgeGroup.fromCode(value);
        if (ageGroup == null) {
            throw new IllegalArgumentException("유효하지 않은 나이 코드입니다");
        }
        return ageGroup.getAge();
    }

    private String resolveEmploymentType(String value) {
        EmploymentType employmentType = EmploymentType.fromCode(value);
        if (employmentType == null) {
            throw new IllegalArgumentException("유효하지 않은 고용 형태 코드입니다");
        }
        return employmentType.getCode();
    }

    private Long resolveIncome(String value) {
        IncomeBracket incomeBracket = IncomeBracket.fromCode(value);
        if (incomeBracket == null) {
            throw new IllegalArgumentException("유효하지 않은 소득 코드입니다");
        }
        return incomeBracket.getIncome();
    }

    private long resolveTargetAmount(String value) {
        String trimmed = value.trim();
        if (!trimmed.matches("\\d+")) {
            throw new IllegalArgumentException("목표 금액은 숫자로 입력해주세요");
        }
        // Long 범위를 넘는 자릿수는 상한 초과 값으로 넘겨 Goal 검증에서 거부되게 한다
        if (trimmed.length() > 18) {
            return Long.MAX_VALUE;
        }
        return Long.parseLong(trimmed);
    }

    private String resolveRegion(String value) {
        Region region = Region.fromCode(value);
        if (region == null) {
            throw new IllegalArgumentException("유효하지 않은 지역 코드입니다");
        }
        return region.getCode();
    }
}