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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    // 대한민국 17개 시/도 단위. value는 현재 Policy.region과 매칭되는 값이 아님(Policy.region이
    // 아직 표준화된 지역 포맷을 갖고 있지 않아 매칭 불가 - PolicyMapper 참고).
    // 1~3단계 CHIP과 동일하게 영문 코드 컨벤션을 사용.
    private static final List<OnboardingStepResponse.OptionDto> REGION_OPTIONS = List.of(
            OnboardingStepResponse.OptionDto.builder().label("서울").value("SEOUL").build(),
            OnboardingStepResponse.OptionDto.builder().label("부산").value("BUSAN").build(),
            OnboardingStepResponse.OptionDto.builder().label("대구").value("DAEGU").build(),
            OnboardingStepResponse.OptionDto.builder().label("인천").value("INCHEON").build(),
            OnboardingStepResponse.OptionDto.builder().label("광주").value("GWANGJU").build(),
            OnboardingStepResponse.OptionDto.builder().label("대전").value("DAEJEON").build(),
            OnboardingStepResponse.OptionDto.builder().label("울산").value("ULSAN").build(),
            OnboardingStepResponse.OptionDto.builder().label("세종").value("SEJONG").build(),
            OnboardingStepResponse.OptionDto.builder().label("경기").value("GYEONGGI").build(),
            OnboardingStepResponse.OptionDto.builder().label("강원").value("GANGWON").build(),
            OnboardingStepResponse.OptionDto.builder().label("충북").value("CHUNGBUK").build(),
            OnboardingStepResponse.OptionDto.builder().label("충남").value("CHUNGNAM").build(),
            OnboardingStepResponse.OptionDto.builder().label("전북").value("JEONBUK").build(),
            OnboardingStepResponse.OptionDto.builder().label("전남").value("JEONNAM").build(),
            OnboardingStepResponse.OptionDto.builder().label("경북").value("GYEONGBUK").build(),
            OnboardingStepResponse.OptionDto.builder().label("경남").value("GYEONGNAM").build(),
            OnboardingStepResponse.OptionDto.builder().label("제주").value("JEJU").build()
    );

    private static final Set<String> VALID_REGION_CODES = REGION_OPTIONS.stream()
            .map(OnboardingStepResponse.OptionDto::getValue)
            .collect(Collectors.toUnmodifiableSet());

    public OnboardingStepResponse getStep(int step) {
        return switch (step) {
            case 1 -> OnboardingStepResponse.builder()
                    .step(1).totalSteps(TOTAL_STEPS).progress(0.2)
                    .question("나이를 알면 받을 수 있는 정책이 달라져요. 어느 시기를 보내고 계신가요?")
                    .answerType("CHIP")
                    .options(List.of(
                            OnboardingStepResponse.OptionDto.builder().label("청년 (~39세)").value("YOUTH").build(),
                            OnboardingStepResponse.OptionDto.builder().label("중장년 (40대~)").value("MIDDLE").build()
                    ))
                    .isComplete(false).build();
            case 2 -> OnboardingStepResponse.builder()
                    .step(2).totalSteps(TOTAL_STEPS).progress(0.4)
                    .question("고용 형태에 따라 신청 가능한 정책이 달라져요. 현재 어떤 일을 하고 계신가요?")
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
                    .question("소득 수준에 맞는 청년 정책을 찾아드릴게요. 월 소득이 어느 정도인가요?")
                    .answerType("CHIP")
                    .options(List.of(
                            OnboardingStepResponse.OptionDto.builder().label("200만원 미만").value("UNDER_2M").build(),
                            OnboardingStepResponse.OptionDto.builder().label("200~300만원").value("200_300").build(),
                            OnboardingStepResponse.OptionDto.builder().label("300만원 이상").value("OVER_3M").build()
                    ))
                    .isComplete(false).build();
            case 4 -> OnboardingStepResponse.builder()
                    .step(4).totalSteps(TOTAL_STEPS).progress(0.8)
                    .question("거주 지역에 따라 받을 수 있는 정책이 달라져요. 현재 어느 지역에 살고 계신가요?")
                    .answerType("CHIP")
                    .options(REGION_OPTIONS)
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
        validateAnswerRequest(request);
        validatePreviousStepCompleted(userId, request.getStep());

        // 답변 DB 저장
        OnboardingAnswer answer = new OnboardingAnswer(
                UUID.fromString(userId),
                request.getStep(),
                request.getValue()
        );

        onboardingAnswerRepository.save(answer);

        // step별 답변을 User 엔티티에 반영 (step1: age, step2: employmentType, step3: income, step4: region)
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
        if (step == 1) return;

        boolean previousStepCompleted = onboardingAnswerRepository
                .existsByUserIdAndStep(UUID.fromString(userId), step - 1);
        if (!previousStepCompleted) {
            throw new IllegalArgumentException("이전 온보딩 단계를 먼저 완료해주세요");
        }
    }

    private void applyAnswerToUser(String userId, int step, String value) {
        if (step != 1 && step != 2 && step != 3 && step != 4) return;

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        switch (step) {
            case 1 -> user.updateAge(resolveAge(value));
            case 2 -> user.updateEmploymentType(value);
            case 3 -> user.updateIncome(resolveIncome(value));
            case 4 -> user.updateRegion(resolveRegion(value));
        }

        userRepository.save(user);
    }

    private Integer resolveAge(String value) {
        Integer age = AGE_CODE_MAP.get(value);
        if (age == null) {
            throw new IllegalArgumentException("유효하지 않은 나이 코드입니다");
        }
        return age;
    }

    private Long resolveIncome(String value) {
        Long income = INCOME_CODE_MAP.get(value);
        if (income == null) {
            throw new IllegalArgumentException("유효하지 않은 소득 코드입니다");
        }
        return income;
    }

    private String resolveRegion(String value) {
        if (!VALID_REGION_CODES.contains(value)) {
            throw new IllegalArgumentException("유효하지 않은 지역 코드입니다");
        }
        return value;
    }
}