package kr.fintarget.api.domain.user.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.fintarget.api.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;
@Getter
@Builder
public class UserProfileResponse {
    @Schema(description = "사용자 ID (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private String userId;

    @Schema(description = "이름. 아직 설정하지 않았으면 null입니다.", example = "홍길동", types = {"string", "null"})
    private String name;

    @Schema(description = "이메일. 아직 설정하지 않았으면 null입니다.", example = "user@example.com", types = {"string", "null"})
    private String email;

    @Schema(description = "거주 지역. 온보딩 4단계를 완료하지 않았으면 null입니다.", example = "서울특별시 강남구", types = {"string", "null"})
    private String region;

    @Schema(description = "가입에 사용한 소셜 로그인 제공자", example = "KAKAO", allowableValues = {"KAKAO", "NAVER", "APPLE"})
    private String provider;
    public static UserProfileResponse from(User user) {
        return UserProfileResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .region(user.getRegion())
                .provider(user.getProvider())
                .build();
    }
}
