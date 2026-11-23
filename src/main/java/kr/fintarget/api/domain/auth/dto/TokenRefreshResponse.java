package kr.fintarget.api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenRefreshResponse(
        @Schema(description = "새로 발급된 액세스 토큰 (JWT)", example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
        @Schema(description = "새로 발급된 리프레시 토큰 (JWT). 저장한 리프레시 토큰을 이 값으로 바꿔야 합니다 (#218)", example = "eyJhbGciOiJIUzI1NiJ9...") String refreshToken,
        @Schema(description = "액세스 토큰 만료까지 남은 시간(초). jwt.expiration 설정값(ms)을 초 단위로 환산한 값입니다.", example = "3600") int expiresIn
) {
}
