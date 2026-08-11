package kr.fintarget.api.domain.policy.client;

public class KinfaApiException extends RuntimeException {

    public KinfaApiException(String message) {
        super(message);
    }

    public KinfaApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
