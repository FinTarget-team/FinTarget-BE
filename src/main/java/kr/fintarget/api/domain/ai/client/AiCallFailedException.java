package kr.fintarget.api.domain.ai.client;

/**
 * OpenAI 호출이 실패함 (서버 오류, 타임아웃, 연결 실패, 요청 거부, 불완전한 응답 등). 502로 응답한다.
 */
public class AiCallFailedException extends AiClientException {

    public static final String MESSAGE = "AI 응답을 가져오지 못했습니다. 잠시 후 다시 시도해주세요.";

    public AiCallFailedException(Reason reason) {
        super(MESSAGE, reason);
    }
}
