package kr.fintarget.api.domain.ai.client;

/**
 * 호출할 모델 등급. 실제 모델 ID는 설정(openai.model.default, openai.model.premium)에서 정한다.
 */
public enum AiModelTier {
    DEFAULT,
    PREMIUM
}
