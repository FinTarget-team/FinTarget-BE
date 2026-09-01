package kr.fintarget.api.domain.auth;

import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.auth.dto.AuthResponse;
import kr.fintarget.api.domain.auth.dto.KakaoTokenLoginRequest;
import kr.fintarget.api.domain.auth.dto.TokenRefreshResponse;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import kr.fintarget.api.security.BlacklistedTokenRepository;
import kr.fintarget.api.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void 유효한_카카오_액세스_토큰이면_기존_회원은_200으로_로그인된다() {
        User existingUser = User.builder()
                .id("user-1")
                .provider("KAKAO")
                .providerId("kakao-123")
                .build();

        when(kakaoOAuthClient.getKakaoUserIdByAccessToken("valid-kakao-access-token")).thenReturn("kakao-123");
        when(userRepository.findByProviderAndProviderId("KAKAO", "kakao-123"))
                .thenReturn(Optional.of(existingUser));
        when(jwtUtil.generateToken("user-1", "KAKAO")).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken("user-1")).thenReturn("refresh-token");

        KakaoTokenLoginRequest request = new KakaoTokenLoginRequest();
        ReflectionTestUtils.setField(request, "accessToken", "valid-kakao-access-token");

        ResponseEntity<ApiResponse<AuthResponse>> response = authController.kakaoTokenLogin(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        AuthResponse data = response.getBody().getData();
        assertThat(data.isNewUser()).isFalse();
        assertThat(data.getUserId()).isEqualTo("user-1");
        assertThat(data.getAccessToken()).isEqualTo("access-token");
        assertThat(data.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(data.getProvider()).isEqualTo("KAKAO");
    }

    @Test
    void 유효한_카카오_액세스_토큰이고_신규_사용자면_회원가입_후_201로_응답한다() {
        User savedUser = User.builder()
                .id("user-2")
                .provider("KAKAO")
                .providerId("kakao-456")
                .build();

        when(kakaoOAuthClient.getKakaoUserIdByAccessToken("new-kakao-access-token")).thenReturn("kakao-456");
        when(userRepository.findByProviderAndProviderId("KAKAO", "kakao-456")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtUtil.generateToken("user-2", "KAKAO")).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken("user-2")).thenReturn("refresh-token");

        KakaoTokenLoginRequest request = new KakaoTokenLoginRequest();
        ReflectionTestUtils.setField(request, "accessToken", "new-kakao-access-token");

        ResponseEntity<ApiResponse<AuthResponse>> response = authController.kakaoTokenLogin(request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().getData().isNewUser()).isTrue();
        assertThat(response.getBody().getData().getUserId()).isEqualTo("user-2");
    }

    @Test
    void 만료되거나_유효하지_않은_카카오_액세스_토큰이면_카카오_API_호출_단계에서_예외가_전파된다() {
        when(kakaoOAuthClient.getKakaoUserIdByAccessToken("expired-kakao-access-token"))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null));

        KakaoTokenLoginRequest request = new KakaoTokenLoginRequest();
        ReflectionTestUtils.setField(request, "accessToken", "expired-kakao-access-token");

        assertThatThrownBy(() -> authController.kakaoTokenLogin(request))
                .isInstanceOf(HttpClientErrorException.class);
    }
}
