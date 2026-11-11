package kr.fintarget.api.domain.ai.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.fintarget.api.domain.ai.chat.service.AiChatService;

import java.util.UUID;

public record AiChatRequest(
        @Schema(description = "질문 (공백 제외 1자 이상, " + AiChatService.MAX_QUESTION_LENGTH + "자 이하)",
                example = "무주택 직장인인데 신청할 수 있나요?", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "질문을 입력해주세요.")
        @Size(max = AiChatService.MAX_QUESTION_LENGTH,
                message = "질문은 " + AiChatService.MAX_QUESTION_LENGTH + "자 이하로 입력해주세요.")
        String question,

        @Schema(description = "이어갈 대화 ID. 생략하면 새 대화를 시작합니다.",
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", types = {"string", "null"})
        UUID conversationId
) {
}
