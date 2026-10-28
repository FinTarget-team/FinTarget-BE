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

    @Schema(description = "전체 단계 수", example = "7")
    private int totalSteps;

    @Schema(description = "진행률 (0.0 ~ 1.0)", example = "0.2")
    private double progress;

    @Schema(description = "현재 단계의 질문 문구", example = "고용 형태에 따라 신청 가능한 정책이 달라져요. 현재 어떤 일을 하고 계신가요?")
    private String question;

    @Schema(description = "질문 응답 UI 형태", example = "CHIP", allowableValues = {"CHIP", "TEXT", "NUMBER", "NONE"})
    private String answerType;

    @Schema(description = "선택형 질문(CHIP)의 선택지 목록. TEXT/NUMBER/NONE 유형이면 빈 배열입니다.")
    private List<OptionDto> options;

    @Schema(description = "온보딩 전체 완료 여부. 마지막 단계(7단계) 답변 제출 완료 시 true입니다.", example = "false")
    private boolean isComplete;
    @Getter
    @Builder
    public static class OptionDto {

        @Schema(description = "선택지에 표시할 라벨", example = "직장인")
        private String label;

        @Schema(description = "선택지 제출 값", example = "EMPLOYED")
        private String value;

    }
}
