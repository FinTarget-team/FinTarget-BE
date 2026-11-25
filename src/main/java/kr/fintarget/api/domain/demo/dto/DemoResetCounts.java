package kr.fintarget.api.domain.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 시연 초기화로 지운 항목별 건수 (#239). 지출·넛지는 지우지 않으므로 여기에 없다 (#253) */
@Schema(description = "초기화로 지운 항목별 건수")
public record DemoResetCounts(
        @Schema(description = "온보딩 답변", example = "7") long onboardingAnswers,
        @Schema(description = "목표 (다시 정해 보관된 이전 목표 포함)", example = "1") long goals,
        @Schema(description = "시뮬레이션", example = "2") long simulations,
        @Schema(description = "저장한 정책", example = "3") long savedPolicies,
        @Schema(description = "단건 채팅 대화", example = "1") long aiConversations,
        @Schema(description = "단건 채팅 메시지", example = "4") long aiConversationMessages,
        @Schema(description = "AI 사용량 기록 (하루 한도 집계)", example = "2") long aiUsages
) {
}
