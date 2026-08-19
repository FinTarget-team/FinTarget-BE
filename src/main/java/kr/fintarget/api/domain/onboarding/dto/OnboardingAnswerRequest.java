package kr.fintarget.api.domain.onboarding.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
@Getter
public class OnboardingAnswerRequest {
    @Schema(description = "답변을 제출할 온보딩 단계 (1~5)", example = "1")
    private int step;

    @Schema(description = "선택/입력한 답변 값 (예: CHIP 단계는 옵션의 value, TEXT/NUMBER 단계는 사용자 입력값)", example = "YOUTH")
    private String value;
}
