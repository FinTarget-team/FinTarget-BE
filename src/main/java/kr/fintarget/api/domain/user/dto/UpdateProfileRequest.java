package kr.fintarget.api.domain.user.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
@Getter
public class UpdateProfileRequest {
    @Schema(description = "변경할 이름. null이면 기존 값을 유지합니다.", example = "홍길동", types = {"string", "null"})
    private String name;

    @Schema(description = "변경할 이메일. null이면 기존 값을 유지합니다.", example = "user@example.com", types = {"string", "null"})
    private String email;

    @Schema(description = "변경할 거주 지역 코드. null이면 기존 값을 유지합니다. 허용되지 않은 코드(빈 문자열 포함)는 400으로 거부됩니다.",
            example = "SEOUL",
            allowableValues = {"SEOUL", "BUSAN", "DAEGU", "INCHEON", "GWANGJU", "DAEJEON", "ULSAN", "SEJONG",
                    "GYEONGGI", "GANGWON", "CHUNGBUK", "CHUNGNAM", "JEONBUK", "JEONNAM", "GYEONGBUK", "GYEONGNAM", "JEJU"},
            types = {"string", "null"})
    private String region;

    @Schema(description = "변경할 연령대 코드. null이면 기존 값을 유지합니다. 허용되지 않은 코드는 400으로 거부됩니다.",
            example = "YOUTH", allowableValues = {"YOUTH", "MIDDLE"}, types = {"string", "null"})
    private String ageGroup;

    @Schema(description = "변경할 고용 형태 코드. null이면 기존 값을 유지합니다. 허용되지 않은 코드는 400으로 거부됩니다.",
            example = "EMPLOYED", allowableValues = {"EMPLOYED", "UNEMPLOYED", "STUDENT", "SELF_EMPLOYED"}, types = {"string", "null"})
    private String employmentType;

    @Schema(description = "변경할 소득 구간 코드. null이면 기존 값을 유지합니다. 허용되지 않은 코드는 400으로 거부됩니다.",
            example = "UNDER_2M", allowableValues = {"UNDER_2M", "200_300", "OVER_3M"}, types = {"string", "null"})
    private String incomeBracket;
}
