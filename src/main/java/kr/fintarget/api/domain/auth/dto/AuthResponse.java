package kr.fintarget.api.domain.auth.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
@Getter
@Builder
public class AuthResponse {
    @Schema(description = "이번 로그인으로 신규 가입되었는지 여부", example = "false")
    private boolean isNewUser;

    @Schema(description = "사용자 ID (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private String userId;

    @Schema(description = "액세스 토큰 (JWT)", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String accessToken;

    @Schema(description = "리프레시 토큰 (JWT)", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String refreshToken;

    @Schema(description = "액세스 토큰 만료까지 남은 시간(초)", example = "3600")
    private int expiresIn;

    @Schema(description = "로그인에 사용한 소셜 로그인 제공자", example = "KAKAO", allowableValues = {"KAKAO", "NAVER", "APPLE"})
    private String provider;
}
