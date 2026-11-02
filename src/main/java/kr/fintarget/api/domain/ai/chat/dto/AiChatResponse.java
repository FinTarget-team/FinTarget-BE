package kr.fintarget.api.domain.ai.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record AiChatResponse(
        @Schema(description = "대화 ID. 이어서 질문할 때 요청에 넣습니다.")
        UUID conversationId,

        @Schema(description = "AI 답변")
        String answer,

        @Schema(description = "이 대화에서 남은 질문 수")
        int remainingTurns,

        @Schema(description = "저장된 정책 원문이 부족해 답변이 부정확할 수 있음. true면 첫 답변 끝에 안내 문구가 붙습니다.")
        boolean sourceLimited,

        @Schema(description = "정책 데이터에 저장된 신청 URL (\"신청하러 가기\" 버튼용). 답변 본문에는 URL을 넣지 않습니다. "
                + "신청 URL이 없거나 http(s) 주소가 아니면 null입니다 (KINFA 정책은 신청 URL이 없어 항상 null).",
                example = "https://young.busan.go.kr/index.nm?menuCd=271", types = {"string", "null"})
        String applyUrl
) {
}
