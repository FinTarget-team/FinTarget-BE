package kr.fintarget.api.domain.auth.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
@Getter
public class NaverLoginRequest {
    @Schema(description = "네이버 OAuth 인가 코드", example = "a1b2c3d4e5f6g7h8i9j0")
    private String authorizationCode;

    @Schema(description = "네이버 OAuth 요청 시 발급받은 state 값 (CSRF 방지용)", example = "e2f1a9c8")
    private String state;
}
