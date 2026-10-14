package kr.fintarget.api.common.exception;

import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.auth.KakaoOAuthException;
import kr.fintarget.api.domain.auth.NaverOAuthException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void NaverOAuthException은_401로_변환된다() {
        ResponseEntity<ApiResponse<?>> response =
                handler.handleNaverOAuth(new NaverOAuthException("유효하지 않은 네이버 액세스 토큰입니다."));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().getMessage()).isEqualTo("유효하지 않은 네이버 액세스 토큰입니다.");
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    void KakaoOAuthException은_401로_변환된다() {
        ResponseEntity<ApiResponse<?>> response =
                handler.handleKakaoOAuth(new KakaoOAuthException("유효하지 않거나 만료된 카카오 인증 정보입니다."));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().getMessage()).isEqualTo("유효하지 않거나 만료된 카카오 인증 정보입니다.");
        assertThat(response.getBody().getData()).isNull();
    }
}
