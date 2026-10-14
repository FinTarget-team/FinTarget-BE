package kr.fintarget.api.domain.auth;

public class KakaoOAuthException extends RuntimeException {
    public KakaoOAuthException(String message) {
        super(message);
    }
}
