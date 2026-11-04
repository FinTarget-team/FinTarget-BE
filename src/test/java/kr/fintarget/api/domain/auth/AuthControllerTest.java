package kr.fintarget.api.domain.auth;

import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.auth.dto.AuthResponse;
import kr.fintarget.api.domain.auth.dto.KakaoTokenLoginRequest;
import kr.fintarget.api.domain.auth.dto.NaverTokenLoginRequest;
import kr.fintarget.api.domain.auth.dto.SocialUserInfo;
import kr.fintarget.api.domain.auth.dto.TokenRefreshResponse;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import kr.fintarget.api.security.BlacklistedTokenRepository;
import kr.fintarget.api.security.JwtUtil;
import kr.fintarget.api.domain.user.code.SocialProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private NaverOAuthClient naverOAuthClient;

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
                .provider(SocialProvider.KAKAO)
                .providerId("kakao-123")
                .build();

        when(kakaoOAuthClient.getKakaoUserByAccessToken("valid-kakao-access-token")).thenReturn(new SocialUserInfo("kakao-123", null));
        when(userRepository.findByProviderAndProviderId(SocialProvider.KAKAO, "kakao-123"))
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
                .provider(SocialProvider.KAKAO)
                .providerId("kakao-456")
                .build();

        when(kakaoOAuthClient.getKakaoUserByAccessToken("new-kakao-access-token")).thenReturn(new SocialUserInfo("kakao-456", null));
        when(userRepository.findByProviderAndProviderId(SocialProvider.KAKAO, "kakao-456")).thenReturn(Optional.empty());
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
    void 유효하지_않은_카카오_액세스_토큰이면_KakaoOAuthException이_전파된다() {
        when(kakaoOAuthClient.getKakaoUserByAccessToken("expired-kakao-access-token"))
                .thenThrow(new KakaoOAuthException("유효하지 않거나 만료된 카카오 인증 정보입니다."));

        KakaoTokenLoginRequest request = new KakaoTokenLoginRequest();
        ReflectionTestUtils.setField(request, "accessToken", "expired-kakao-access-token");

        assertThatThrownBy(() -> authController.kakaoTokenLogin(request))
                .isInstanceOf(KakaoOAuthException.class)
                .hasMessage("유효하지 않거나 만료된 카카오 인증 정보입니다.");
    }

    @Test
    void 유효한_네이버_액세스_토큰이면_기존_회원은_200으로_로그인된다() {
        User existingUser = User.builder()
                .id("user-3")
                .provider(SocialProvider.NAVER)
                .providerId("naver-123")
                .build();

        when(naverOAuthClient.getNaverUser("valid-naver-access-token")).thenReturn(new SocialUserInfo("naver-123", null));
        when(userRepository.findByProviderAndProviderId(SocialProvider.NAVER, "naver-123"))
                .thenReturn(Optional.of(existingUser));
        when(jwtUtil.generateToken("user-3", "NAVER")).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken("user-3")).thenReturn("refresh-token");

        NaverTokenLoginRequest request = new NaverTokenLoginRequest();
        ReflectionTestUtils.setField(request, "accessToken", "valid-naver-access-token");

        ResponseEntity<ApiResponse<AuthResponse>> response = authController.naverTokenLogin(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        AuthResponse data = response.getBody().getData();
        assertThat(data.isNewUser()).isFalse();
        assertThat(data.getUserId()).isEqualTo("user-3");
        assertThat(data.getAccessToken()).isEqualTo("access-token");
        assertThat(data.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(data.getProvider()).isEqualTo("NAVER");
    }

    @Test
    void 유효한_네이버_액세스_토큰이고_신규_사용자면_회원가입_후_201로_응답한다() {
        User savedUser = User.builder()
                .id("user-4")
                .provider(SocialProvider.NAVER)
                .providerId("naver-456")
                .build();

        when(naverOAuthClient.getNaverUser("new-naver-access-token")).thenReturn(new SocialUserInfo("naver-456", null));
        when(userRepository.findByProviderAndProviderId(SocialProvider.NAVER, "naver-456")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtUtil.generateToken("user-4", "NAVER")).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken("user-4")).thenReturn("refresh-token");

        NaverTokenLoginRequest request = new NaverTokenLoginRequest();
        ReflectionTestUtils.setField(request, "accessToken", "new-naver-access-token");

        ResponseEntity<ApiResponse<AuthResponse>> response = authController.naverTokenLogin(request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().getData().isNewUser()).isTrue();
        assertThat(response.getBody().getData().getUserId()).isEqualTo("user-4");
    }

    @Test
    void 유효하지_않은_네이버_액세스_토큰이면_NaverOAuthException이_전파된다() {
        when(naverOAuthClient.getNaverUser("expired-naver-access-token"))
                .thenThrow(new NaverOAuthException("유효하지 않은 네이버 액세스 토큰입니다."));

        NaverTokenLoginRequest request = new NaverTokenLoginRequest();
        ReflectionTestUtils.setField(request, "accessToken", "expired-naver-access-token");

        assertThatThrownBy(() -> authController.naverTokenLogin(request))
                .isInstanceOf(NaverOAuthException.class)
                .hasMessage("유효하지 않은 네이버 액세스 토큰입니다.");
    }

    @Test
    void 신규_가입이면_소셜_제공자의_이메일과_provider를_함께_저장한다() {
        when(kakaoOAuthClient.getKakaoUserByAccessToken("token"))
                .thenReturn(new SocialUserInfo("kakao-789", "social@kakao.com"));
        when(userRepository.findByProviderAndProviderId(SocialProvider.KAKAO, "kakao-789")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authController.kakaoTokenLogin(kakaoRequest("token"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("social@kakao.com");
        assertThat(saved.getValue().getProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(saved.getValue().getProviderId()).isEqualTo("kakao-789");
    }

    @Test
    void 이메일_제공에_동의하지_않은_신규_가입이면_이메일은_null로_저장된다() {
        when(naverOAuthClient.getNaverUser("token")).thenReturn(new SocialUserInfo("naver-789", null));
        when(userRepository.findByProviderAndProviderId(SocialProvider.NAVER, "naver-789")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<ApiResponse<AuthResponse>> response = authController.naverTokenLogin(naverRequest("token"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isNull();
        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void 기존_회원의_이메일이_비어_있으면_로그인할_때_소셜_제공자의_이메일로_채운다() {
        User existingUser = User.builder().id("user-5").provider(SocialProvider.NAVER).providerId("naver-555").build();
        when(naverOAuthClient.getNaverUser("token")).thenReturn(new SocialUserInfo("naver-555", "social@naver.com"));
        when(userRepository.findByProviderAndProviderId(SocialProvider.NAVER, "naver-555")).thenReturn(Optional.of(existingUser));

        authController.naverTokenLogin(naverRequest("token"));

        assertThat(existingUser.getEmail()).isEqualTo("social@naver.com");
        verify(userRepository).save(existingUser);
    }

    @Test
    void 기존_회원의_이메일이_있으면_소셜_제공자의_이메일로_덮어쓰지_않는다() {
        User existingUser = User.builder().id("user-6").provider(SocialProvider.KAKAO).providerId("kakao-666")
                .email("saved@example.com").build();
        when(kakaoOAuthClient.getKakaoUserByAccessToken("token")).thenReturn(new SocialUserInfo("kakao-666", "other@kakao.com"));
        when(userRepository.findByProviderAndProviderId(SocialProvider.KAKAO, "kakao-666")).thenReturn(Optional.of(existingUser));

        authController.kakaoTokenLogin(kakaoRequest("token"));

        assertThat(existingUser.getEmail()).isEqualTo("saved@example.com");
        verify(userRepository, never()).save(any(User.class));
    }

    private static KakaoTokenLoginRequest kakaoRequest(String accessToken) {
        KakaoTokenLoginRequest request = new KakaoTokenLoginRequest();
        ReflectionTestUtils.setField(request, "accessToken", accessToken);
        return request;
    }

    private static NaverTokenLoginRequest naverRequest(String accessToken) {
        NaverTokenLoginRequest request = new NaverTokenLoginRequest();
        ReflectionTestUtils.setField(request, "accessToken", accessToken);
        return request;
    }
}
