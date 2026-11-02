package kr.fintarget.mockbank.webhook;

/**
 * 메인 서버로 전송하지 못했거나 메인 서버가 거부한 경우. 메시지는 화면에 그대로 보여준다
 */
public class WebhookSendException extends RuntimeException {

    public WebhookSendException(String message) {
        super(message);
    }

    public WebhookSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
