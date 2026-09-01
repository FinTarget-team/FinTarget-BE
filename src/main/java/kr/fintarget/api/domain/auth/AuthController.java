package kr.fintarget.api.domain.auth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.auth.dto.AppleLoginRequest;
import kr.fintarget.api.domain.auth.dto.AuthResponse;
import kr.fintarget.api.domain.auth.dto.KakaoLoginRequest;
import kr.fintarget.api.domain.auth.dto.KakaoTokenLoginRequest;
import kr.fintarget.api.domain.auth.dto.NaverLoginRequest;
import kr.fintarget.api.domain.auth.dto.TokenRefreshResponse;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import kr.fintarget.api.security.BlacklistedToken;
import kr.fintarget.api.security.BlacklistedTokenRepository;
import kr.fintarget.api.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "인증", description = "소셜 로그인, 토큰 재발급, 로그아웃 API. 이 도메인의 엔드포인트는 인증 없이 호출 가능합니다.")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final BlacklistedTokenRepository blacklistedTokenRepository;

    private ResponseEntity<ApiResponse<AuthResponse>> socialLogin(String providerId, String provider) {
        boolean isNewUser = !userRepository.findByProviderAndProviderId(provider, providerId).isPresent();
        User user = userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .provider(provider)
                                .providerId(providerId)
                                .build()
                ));
        String accessToken = jwtUtil.generateToken(user.getId(), provider);
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        AuthResponse response = AuthResponse.builder()
                .isNewUser(isNewUser)
                .userId(user.getId())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(3600)
                .provider(provider)
                .build();
        return ResponseEntity.status(isNewUser ? 201 : 200).body(
                isNewUser ? ApiResponse.created(response) : ApiResponse.ok(response)
        );
    }

    @Operation(
            summary = "카카오 소셜 로그인",
            description = "인가 코드(authorizationCode)로 카카오 사용자 정보를 조회해 로그인/회원가입을 처리합니다. " +
                    "기존 회원이면 200, 신규 회원이면 자동 가입 후 201로 응답합니다. 응답 바디 구조는 두 경우 동일합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "기존 회원 로그인 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "신규 회원 자동 가입 및 로그인 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "카카오 인증 서버 호출 실패 등 처리 중 오류. message는 원인 예외 메시지이며 null일 수 있습니다.",
                    content = @Content(examples = @ExampleObject(
                            name = "서버 오류",
                            value = """
                                    {"status": 500, "message": null, "data": null}
                                    """
                    ))
            )
    })
    @PostMapping("/kakao/login")
    public ResponseEntity<ApiResponse<AuthResponse>> kakaoLogin(@RequestBody KakaoLoginRequest request) {
        String kakaoUserId = kakaoOAuthClient.getKakaoUserId(request.getAuthorizationCode());
        return socialLogin(kakaoUserId, "KAKAO");
    }

    @Operation(
            summary = "카카오 액세스 토큰 로그인 (네이티브 앱 전용)",
            description = "iOS/Android 카카오 SDK 로그인 성공 시 SDK가 발급한 카카오 액세스 토큰(accessToken)으로 카카오 사용자 정보를 조회해 " +
                    "로그인/회원가입을 처리합니다. /auth/kakao/login과 달리 서버가 카카오 토큰 교환 API를 호출하지 않고 전달받은 " +
                    "액세스 토큰을 그대로 사용하므로 redirect_uri가 필요 없습니다. " +
                    "기존 회원이면 200, 신규 회원이면 자동 가입 후 201로 응답합니다. 응답 바디 구조는 두 경우 동일합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "기존 회원 로그인 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "신규 회원 자동 가입 및 로그인 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "카카오 인증 서버 호출 실패 등 처리 중 오류. message는 원인 예외 메시지이며 null일 수 있습니다.",
                    content = @Content(examples = @ExampleObject(
                            name = "서버 오류",
                            value = """
                                    {"status": 500, "message": null, "data": null}
                                    """
                    ))
            )
    })
    @PostMapping("/kakao/login/token")
    public ResponseEntity<ApiResponse<AuthResponse>> kakaoTokenLogin(@RequestBody KakaoTokenLoginRequest request) {
        String kakaoUserId = kakaoOAuthClient.getKakaoUserIdByAccessToken(request.getAccessToken());
        return socialLogin(kakaoUserId, "KAKAO");
    }

    @Operation(
            summary = "네이버 소셜 로그인",
            description = "인가 코드(authorizationCode)로 네이버 사용자 정보를 조회해 로그인/회원가입을 처리합니다. " +
                    "기존 회원이면 200, 신규 회원이면 자동 가입 후 201로 응답합니다. 응답 바디 구조는 두 경우 동일합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "기존 회원 로그인 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "신규 회원 자동 가입 및 로그인 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "네이버 인증 서버 호출 실패 등 처리 중 오류. message는 원인 예외 메시지이며 null일 수 있습니다.",
                    content = @Content(examples = @ExampleObject(
                            name = "서버 오류",
                            value = """
                                    {"status": 500, "message": null, "data": null}
                                    """
                    ))
            )
    })
    @PostMapping("/naver/login")
    public ResponseEntity<ApiResponse<AuthResponse>> naverLogin(@RequestBody NaverLoginRequest request) {
        return socialLogin(request.getAuthorizationCode(), "NAVER");
    }

    @Operation(
            summary = "애플 소셜 로그인",
            description = "식별 토큰(identityToken)으로 애플 사용자 정보를 조회해 로그인/회원가입을 처리합니다. " +
                    "기존 회원이면 200, 신규 회원이면 자동 가입 후 201로 응답합니다. 응답 바디 구조는 두 경우 동일합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "기존 회원 로그인 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "신규 회원 자동 가입 및 로그인 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "애플 인증 서버 호출 실패 등 처리 중 오류. message는 원인 예외 메시지이며 null일 수 있습니다.",
                    content = @Content(examples = @ExampleObject(
                            name = "서버 오류",
                            value = """
                                    {"status": 500, "message": null, "data": null}
                                    """
                    ))
            )
    })
    @PostMapping("/apple/login")
    public ResponseEntity<ApiResponse<AuthResponse>> appleLogin(@RequestBody AppleLoginRequest request) {
        return socialLogin(request.getIdentityToken(), "APPLE");
    }

    @Operation(
            summary = "액세스 토큰 재발급",
            description = "유효한 리프레시 토큰으로 새 액세스 토큰을 발급합니다. 리프레시 토큰 자체는 재발급되지 않습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "액세스 토큰 재발급 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "리프레시 토큰이 유효하지 않거나 만료됨/블랙리스트에 등록됨",
                    content = @Content(examples = @ExampleObject(
                            name = "유효하지 않은 리프레시 토큰",
                            value = """
                                    {"status": 401, "message": "Invalid refresh token", "data": null}
                                    """
                    ))
            )
    })
    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refreshToken(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (!jwtUtil.validateToken(refreshToken)) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "Invalid refresh token"));
        }
        String userId = jwtUtil.getUserId(refreshToken);
        String newAccessToken = jwtUtil.generateToken(userId, "REFRESH");
        int expiresIn = (int) (jwtUtil.getExpirationMillis() / 1000);
        return ResponseEntity.ok(ApiResponse.ok(new TokenRefreshResponse(newAccessToken, expiresIn)));
    }

    @Operation(
            summary = "로그아웃",
            description = "Authorization 헤더의 액세스 토큰과(있다면) 바디의 리프레시 토큰을 블랙리스트에 등록해 즉시 무효화합니다. " +
                    "헤더/바디가 모두 없어도 200으로 응답하며(멱등 처리), 토큰 파싱에 실패해도 오류를 반환하지 않고 조용히 무시됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "로그아웃 처리 성공, data는 항상 null",
                    useReturnTypeSchema = true
            )
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<?>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map<String, String> body) {

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            blacklistToken(authHeader.substring(7));
        }

        if (body != null && body.get("refreshToken") != null) {
            blacklistToken(body.get("refreshToken"));
        }

        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private void blacklistToken(String token) {
        try {
            String jti = jwtUtil.getJti(token);
            if (jti == null) return;
            if (!blacklistedTokenRepository.existsById(jti)) {
                blacklistedTokenRepository.save(new BlacklistedToken(jti, jwtUtil.getExpirationTime(token)));
            }
        } catch (Exception ignored) {
        }
    }
}
