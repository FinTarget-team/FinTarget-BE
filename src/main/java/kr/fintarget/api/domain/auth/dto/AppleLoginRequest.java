package kr.fintarget.api.domain.auth.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
@Getter
public class AppleLoginRequest {
    @Schema(description = "애플 ID 토큰 (JWT)", example = "eyJhbGciOiJSUzI1NiJ9...")
    private String identityToken;

    @Schema(description = "애플 OAuth 인가 코드", example = "a1b2c3d4e5f6g7h8i9j0")
    private String authorizationCode;

    @Schema(description = "애플이 최초 로그인 시에만 제공하는 사용자 이름. 이후 로그인부터는 애플이 내려주지 않아 null입니다.", example = "홍길동", types = {"string", "null"})
    private String fullName;

    @Schema(description = "애플이 최초 로그인 시에만 제공하는 이메일. 이후 로그인부터는 애플이 내려주지 않아 null입니다.", example = "user@privaterelay.appleid.com", types = {"string", "null"})
    private String email;
}
