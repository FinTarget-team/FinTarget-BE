package kr.fintarget.api.domain.auth.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
@Getter
public class NaverTokenLoginRequest {
    @Schema(description = "iOS/Android 네이버 SDK 로그인 성공 시 SDK가 발급한 네이버 액세스 토큰", example = "AAAAO9k7v3RCfbzPwAbcDeFgHiJkLmNoPqRsTuVwXyZ")
    private String accessToken;
}
