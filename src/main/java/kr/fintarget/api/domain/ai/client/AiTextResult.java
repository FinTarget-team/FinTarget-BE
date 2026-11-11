package kr.fintarget.api.domain.ai.client;

/**
 * @param model           응답에 기록된 실제 모델명
 * @param outputTokens    추론 토큰을 포함한 출력 토큰 수
 * @param reasoningTokens 출력 토큰 중 추론 토큰 수
 */
public record AiTextResult(String text, String model, int inputTokens, int outputTokens, int reasoningTokens) {
}
