package kr.fintarget.api.domain.auth;

import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.auth.dto.TokenRefreshResponse;
import kr.fintarget.api.domain.user.repository.UserRepository;
import kr.fintarget.api.security.BlacklistedTokenRepository;
import kr.fintarget.api.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserRepository userRepository;

    @Mock
    private KakaoOAuthClient kakaoOAuthClient;

    @Mock
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @InjectMocks
    private AuthController authController;

    @Test
    void 리프레시_토큰이_유효하면_jwt_expiration_설정값을_초로_환산해서_반환한다() {
        when(jwtUtil.validateToken("valid-refresh-token")).thenReturn(true);
        when(jwtUtil.getUserId("valid-refresh-token")).thenReturn("user-1");
        when(jwtUtil.generateToken("user-1", "REFRESH")).thenReturn("new-access-token");
        when(jwtUtil.getExpirationMillis()).thenReturn(3_600_000L);

        ResponseEntity<ApiResponse<TokenRefreshResponse>> response =
                authController.refreshToken(Map.of("refreshToken", "valid-refresh-token"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        TokenRefreshResponse data = response.getBody().getData();
        assertThat(data.accessToken()).isEqualTo("new-access-token");
        assertThat(data.expiresIn()).isEqualTo(3600);
    }

    @Test
    void jwt_expiration_설정이_바뀌면_expiresIn도_그에_맞게_계산된다() {
        when(jwtUtil.validateToken("valid-refresh-token")).thenReturn(true);
        when(jwtUtil.getUserId("valid-refresh-token")).thenReturn("user-1");
        when(jwtUtil.generateToken("user-1", "REFRESH")).thenReturn("new-access-token");
        when(jwtUtil.getExpirationMillis()).thenReturn(7_200_000L);

        ResponseEntity<ApiResponse<TokenRefreshResponse>> response =
                authController.refreshToken(Map.of("refreshToken", "valid-refresh-token"));

        assertThat(response.getBody().getData().expiresIn()).isEqualTo(7200);
    }

    @Test
    void 리프레시_토큰이_유효하지_않으면_401과_함께_data는_null을_반환한다() {
        when(jwtUtil.validateToken("invalid-refresh-token")).thenReturn(false);

        ResponseEntity<ApiResponse<TokenRefreshResponse>> response =
                authController.refreshToken(Map.of("refreshToken", "invalid-refresh-token"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid refresh token");
        assertThat(response.getBody().getData()).isNull();
    }
}
