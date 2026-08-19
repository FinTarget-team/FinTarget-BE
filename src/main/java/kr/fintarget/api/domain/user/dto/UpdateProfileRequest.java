package kr.fintarget.api.domain.user.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
@Getter
public class UpdateProfileRequest {
    @Schema(description = "변경할 이름. null이면 기존 값을 유지합니다.", example = "홍길동", types = {"string", "null"})
    private String name;

    @Schema(description = "변경할 이메일. null이면 기존 값을 유지합니다.", example = "user@example.com", types = {"string", "null"})
    private String email;

    @Schema(description = "변경할 거주 지역. null이면 기존 값을 유지합니다.", example = "서울특별시 강남구", types = {"string", "null"})
    private String region;
}
