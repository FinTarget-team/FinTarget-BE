package kr.fintarget.api.domain.policy.client;

public class YouthCenterApiException extends RuntimeException {

    public YouthCenterApiException(String message) {
        super(message);
    }

    public YouthCenterApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
