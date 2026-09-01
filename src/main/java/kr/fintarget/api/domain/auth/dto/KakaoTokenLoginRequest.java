package kr.fintarget.api.domain.auth.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
@Getter
public class KakaoTokenLoginRequest {
    @Schema(description = "iOS/Android 카카오 SDK 로그인 성공 시 SDK가 발급한 카카오 액세스 토큰", example = "qwer1234asdf5678zxcv9012")
    private String accessToken;
}
