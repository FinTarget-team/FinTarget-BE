package kr.fintarget.api.domain.nudge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record NudgeListResponse(
        @Schema(description = "넛지 목록 (최신순)") List<NudgeResponse> nudges,
        @Schema(description = "읽지 않은 넛지 개수", example = "2") long unreadCount
) {
}
