package kr.fintarget.api.domain.ai.client;

import java.util.List;

/**
 * @param systemPrompt    시스템 프롬프트 (없으면 null)
 * @param messages        대화 메시지 (오래된 순, 1개 이상)
 * @param tier            모델 등급 (null이면 DEFAULT)
 * @param maxOutputTokens 이 요청의 출력 토큰 상한 (추론 토큰 포함). null이면 설정값 openai.max-output-tokens
 */
public record AiTextRequest(String systemPrompt, List<AiMessage> messages, AiModelTier tier, Integer maxOutputTokens) {

    public AiTextRequest {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("AI 요청 메시지가 비어 있습니다.");
        }
        if (maxOutputTokens != null && maxOutputTokens < 1) {
            throw new IllegalArgumentException("출력 토큰 상한은 1 이상이어야 합니다: " + maxOutputTokens);
        }
        messages = List.copyOf(messages);
        tier = tier == null ? AiModelTier.DEFAULT : tier;
    }

    public AiTextRequest(String systemPrompt, List<AiMessage> messages, AiModelTier tier) {
        this(systemPrompt, messages, tier, null);
    }
}
