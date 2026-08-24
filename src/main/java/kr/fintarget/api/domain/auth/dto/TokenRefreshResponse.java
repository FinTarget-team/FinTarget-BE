package kr.fintarget.api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenRefreshResponse(
        @Schema(description = "새로 발급된 액세스 토큰 (JWT)", example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
        @Schema(description = "액세스 토큰 만료까지 남은 시간(초). jwt.expiration 설정값(ms)을 초 단위로 환산한 값입니다.", example = "3600") int expiresIn
) {
}
