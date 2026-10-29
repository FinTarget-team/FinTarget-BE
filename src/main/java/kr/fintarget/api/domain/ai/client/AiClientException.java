package kr.fintarget.api.domain.ai.client;

/**
 * AI 호출 실패. 메시지는 사용자에게 그대로 보여도 되는 고정 문구이며,
 * OpenAI 응답 본문, error.message, API 키를 담지 않는다 (원인 예외도 연결하지 않음).
 */
public abstract class AiClientException extends RuntimeException {

    public enum Reason {
        // AiUnavailableException (503)
        DISABLED,
        RATE_LIMITED,
        QUOTA_EXCEEDED,
        OVERLOADED,
        // AiCallFailedException (502)
        SERVER_ERROR,
        TIMEOUT,
        CONNECTION_FAILED,
        REQUEST_REJECTED,
        AUTH_FAILED,
        INCOMPLETE,
        INVALID_RESPONSE,
        INTERRUPTED
    }

    private final Reason reason;

    protected AiClientException(String message, Reason reason) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public static AiClientException of(Reason reason) {
        return switch (reason) {
            case DISABLED, RATE_LIMITED, QUOTA_EXCEEDED, OVERLOADED -> new AiUnavailableException(reason);
            default -> new AiCallFailedException(reason);
        };
    }
}
