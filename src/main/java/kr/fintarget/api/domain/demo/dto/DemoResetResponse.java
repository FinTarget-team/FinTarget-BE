package kr.fintarget.api.domain.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 시연 초기화 결과 (#239) */
@Schema(description = "시연 초기화 결과")
public record DemoResetResponse(
        @Schema(description = "초기화 성공 여부. 실패는 예외로 응답하므로 성공 응답에서는 항상 true", example = "true") boolean reset,
        @Schema(description = "초기화한 사용자 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") String userId,
        @Schema(description = "지운 항목별 건수") DemoResetCounts deleted,
        @Schema(description = "지우지 않고 남긴 지출 건수 (mock-bank 거래를 시연마다 다시 만들 수 없어 지출은 남긴다)",
                example = "13") long keptExpenses,
        @Schema(description = "지우지 않고 남긴 넛지·알림 건수 (#253)", example = "5") long keptNudges
) {

    public static DemoResetResponse of(String userId, DemoResetResult result) {
        return new DemoResetResponse(true, userId, result.deleted(), result.keptExpenses(), result.keptNudges());
    }
}
