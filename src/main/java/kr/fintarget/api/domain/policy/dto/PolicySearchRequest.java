package kr.fintarget.api.domain.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.fintarget.api.domain.ai.chat.service.AiChatService;

public record PolicySearchRequest(
        @Schema(description = "자연어 질문 (공백 제외 1자 이상, " + AiChatService.MAX_QUESTION_LENGTH + "자 이하)",
                example = "월세 지원받을 수 있는 정책 알려줘", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "질문을 입력해주세요.")
        // 길이 상한은 단건 정책 채팅과 같은 값을 쓴다 (질문 성격이 같고 두 곳이 어긋나면 안내 문구가 달라진다)
        @Size(max = AiChatService.MAX_QUESTION_LENGTH,
                message = "질문은 " + AiChatService.MAX_QUESTION_LENGTH + "자 이하로 입력해주세요.")
        String question
) {
}
