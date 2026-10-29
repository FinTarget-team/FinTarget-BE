package kr.fintarget.api.domain.ai.client;

import java.util.List;

/**
 * @param systemPrompt 시스템 프롬프트 (없으면 null)
 * @param messages     대화 메시지 (오래된 순, 1개 이상)
 * @param tier         모델 등급 (null이면 DEFAULT)
 */
public record AiTextRequest(String systemPrompt, List<AiMessage> messages, AiModelTier tier) {

    public AiTextRequest {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("AI 요청 메시지가 비어 있습니다.");
        }
        messages = List.copyOf(messages);
        tier = tier == null ? AiModelTier.DEFAULT : tier;
    }
}
