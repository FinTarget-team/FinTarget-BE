package kr.fintarget.api.domain.ai.client;

/**
 * 지금은 AI를 쓸 수 없음 (키 없음, 호출 한도·잔액 초과, OpenAI 과부하). 503으로 응답한다.
 */
public class AiUnavailableException extends AiClientException {

    public static final String MESSAGE = "AI 기능을 지금 사용할 수 없습니다. 잠시 후 다시 시도해주세요.";

    public AiUnavailableException(Reason reason) {
        super(MESSAGE, reason);
    }
}
