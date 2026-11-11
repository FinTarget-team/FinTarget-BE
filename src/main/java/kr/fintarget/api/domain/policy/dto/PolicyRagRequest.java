package kr.fintarget.api.domain.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.fintarget.api.domain.ai.chat.service.AiChatService;

public record PolicyRagRequest(
        @Schema(description = "자연어 질문 (공백 제외 1자 이상, " + AiChatService.MAX_QUESTION_LENGTH + "자 이하)",
                example = "자취 중인 직장인인데 월세 지원받을 수 있는 정책 있어?", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "질문을 입력해주세요.")
        // 길이 상한은 단건 정책 채팅, 정책 검색과 같은 값을 쓴다
        @Size(max = AiChatService.MAX_QUESTION_LENGTH,
                message = "질문은 " + AiChatService.MAX_QUESTION_LENGTH + "자 이하로 입력해주세요.")
        String question
) {
}
