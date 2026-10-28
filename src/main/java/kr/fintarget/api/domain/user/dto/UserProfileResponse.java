package kr.fintarget.api.domain.user.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.fintarget.api.domain.user.code.HousingStatus;
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

    @Schema(description = "거주 지역. 온보딩 5단계를 완료하지 않았으면 null입니다.", example = "SEOUL", types = {"string", "null"})
    private String region;

    @Schema(description = "가입에 사용한 소셜 로그인 제공자", example = "KAKAO", allowableValues = {"KAKAO", "NAVER"})
    private String provider;

    @Schema(description = "출생연도. 온보딩 나이 단계를 완료하지 않았거나 출생연도 입력 도입 전에 연령대만 입력한 사용자는 null입니다.",
            example = "2000", types = {"integer", "null"})
    private Integer birthYear;

    @Schema(description = "고용 형태 코드. 온보딩 3단계를 완료하지 않았으면 null입니다.",
            example = "EMPLOYED", allowableValues = {"EMPLOYED", "UNEMPLOYED", "STUDENT", "SELF_EMPLOYED"}, types = {"string", "null"})
    private String employmentType;

    @Schema(description = "세전 월 소득 구간 코드. 온보딩 4단계를 완료하지 않았거나 구간 개편 전에 저장된 값이면 null입니다.",
            example = "300_380", allowableValues = {"UNDER_150", "150_260", "260_300", "300_380", "380_410", "OVER_410"},
            types = {"string", "null"})
    private String incomeBracket;

    @Schema(description = "주택 보유 여부 코드. 선택 안 함을 고른 사용자와 주택 단계를 아직 답하지 않은 사용자는 모두 NOT_SPECIFIED입니다 (null로 내려가지 않음).",
            example = "HOMELESS", allowableValues = {"HOMELESS", "HOMEOWNER", "NOT_SPECIFIED"})
    private String housingStatus;

    @Schema(description = "온보딩 전체 완료 여부", example = "true")
    private boolean onboardingCompleted;

    public static UserProfileResponse from(User user) {
        IncomeBracket incomeBracket = IncomeBracket.fromIncome(user.getIncome());

        return UserProfileResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .region(user.getRegion())
                .provider(user.getProvider())
                .birthYear(user.getBirthYear())
                .employmentType(user.getEmploymentType())
                .incomeBracket(incomeBracket != null ? incomeBracket.getCode() : null)
                .housingStatus(HousingStatus.responseCodeOf(user.getHousingStatus()))
                .onboardingCompleted(user.isOnboardingCompleted())
                .build();
    }
}
