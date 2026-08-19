package kr.fintarget.api.domain.auth.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
@Getter
public class KakaoLoginRequest {
    @Schema(description = "카카오 OAuth 인가 코드", example = "a1b2c3d4e5f6g7h8i9j0")
    private String authorizationCode;

    @Schema(description = "카카오 OAuth 요청 시 사용한 redirect_uri", example = "https://fintarget.kr/oauth/kakao/callback")
    private String redirectUri;
}
