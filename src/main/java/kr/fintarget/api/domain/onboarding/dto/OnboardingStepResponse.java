package kr.fintarget.api.domain.onboarding.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import java.util.List;
@Getter
@Builder
public class OnboardingStepResponse {
    @Schema(description = "현재 단계 번호", example = "1")
    private int step;

    @Schema(description = "전체 단계 수", example = "5")
    private int totalSteps;

    @Schema(description = "진행률 (0.0 ~ 1.0)", example = "0.2")
    private double progress;

    @Schema(description = "현재 단계의 질문 문구", example = "나이를 알면 받을 수 있는 정책이 달라져요! 어느 시기를 보내고 계신가요?")
    private String question;

    @Schema(description = "질문 응답 UI 형태", example = "CHIP", allowableValues = {"CHIP", "TEXT", "NUMBER", "NONE"})
    private String answerType;

    @Schema(description = "선택형 질문(CHIP)의 선택지 목록. TEXT/NUMBER/NONE 유형이면 빈 배열입니다.")
    private List<OptionDto> options;

    @Schema(description = "온보딩 전체 완료 여부. 5단계 답변 제출 완료 시 true입니다.", example = "false")
    private boolean isComplete;
    @Getter
    @Builder
    public static class OptionDto {

        @Schema(description = "선택지에 표시할 라벨", example = "청년 (~39세)")
        private String label;

        @Schema(description = "선택지 제출 값", example = "YOUTH")
        private String value;

    }
}
