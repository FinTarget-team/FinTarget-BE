package kr.fintarget.api.domain.user.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.fintarget.api.domain.user.code.AgeGroup;
import kr.fintarget.api.domain.user.code.IncomeBracket;
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

    @Schema(description = "거주 지역. 온보딩 4단계를 완료하지 않았으면 null입니다.", example = "SEOUL", types = {"string", "null"})
    private String region;

    @Schema(description = "가입에 사용한 소셜 로그인 제공자", example = "KAKAO", allowableValues = {"KAKAO", "NAVER", "APPLE"})
    private String provider;

    @Schema(description = "연령대 코드. 온보딩 1단계를 완료하지 않았으면 null입니다.",
            example = "YOUTH", allowableValues = {"YOUTH", "MIDDLE"}, types = {"string", "null"})
    private String ageGroup;

    @Schema(description = "고용 형태 코드. 온보딩 2단계를 완료하지 않았으면 null입니다.",
            example = "EMPLOYED", allowableValues = {"EMPLOYED", "UNEMPLOYED", "STUDENT", "SELF_EMPLOYED"}, types = {"string", "null"})
    private String employmentType;

    @Schema(description = "소득 구간 코드. 온보딩 3단계를 완료하지 않았으면 null입니다.",
            example = "UNDER_2M", allowableValues = {"UNDER_2M", "200_300", "OVER_3M"}, types = {"string", "null"})
    private String incomeBracket;

    @Schema(description = "온보딩 전체 완료 여부", example = "true")
    private boolean onboardingCompleted;

    public static UserProfileResponse from(User user) {
        AgeGroup ageGroup = AgeGroup.fromAge(user.getAge());
        IncomeBracket incomeBracket = IncomeBracket.fromIncome(user.getIncome());

        return UserProfileResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .region(user.getRegion())
                .provider(user.getProvider())
                .ageGroup(ageGroup != null ? ageGroup.getCode() : null)
                .employmentType(user.getEmploymentType())
                .incomeBracket(incomeBracket != null ? incomeBracket.getCode() : null)
                .onboardingCompleted(user.isOnboardingCompleted())
                .build();
    }
}
