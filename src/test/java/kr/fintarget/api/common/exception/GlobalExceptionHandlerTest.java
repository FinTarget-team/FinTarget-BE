package kr.fintarget.api.common.exception;

import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.ai.client.AiCallFailedException;
import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiUnavailableException;
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
    void AiUnavailableException은_503과_고정_문구로_변환된다() {
        ResponseEntity<ApiResponse<?>> response =
                handler.handleAiUnavailable(new AiUnavailableException(AiClientException.Reason.DISABLED));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().getStatus()).isEqualTo(503);
        assertThat(response.getBody().getMessage()).isEqualTo(AiUnavailableException.MESSAGE);
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    void AiCallFailedException은_502와_고정_문구로_변환된다() {
        ResponseEntity<ApiResponse<?>> response =
                handler.handleAiCallFailed(new AiCallFailedException(AiClientException.Reason.TIMEOUT));

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody().getStatus()).isEqualTo(502);
        assertThat(response.getBody().getMessage()).isEqualTo(AiCallFailedException.MESSAGE);
    }

    @Test
    void AI_예외는_IllegalStateException이_아니다() {
        // IllegalStateException 핸들러(409)로 잘못 매핑되지 않도록
        assertThat(IllegalStateException.class.isAssignableFrom(AiClientException.class)).isFalse();
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
