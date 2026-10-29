package kr.fintarget.api.domain.ai.client;

/**
 * 텍스트 생성 클라이언트. 실패 시 AiClientException(AiUnavailableException 503, AiCallFailedException 502)을 던진다.
 */
public interface AiTextClient {

    AiTextResult generate(AiTextRequest request);
}
