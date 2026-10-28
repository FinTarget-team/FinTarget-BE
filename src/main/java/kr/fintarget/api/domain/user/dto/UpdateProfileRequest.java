package kr.fintarget.api.domain.user.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
@Getter
public class UpdateProfileRequest {
    @Schema(description = "변경할 이름. null이면 기존 값을 유지합니다. 앞뒤 공백은 제거되어 저장되며, 공백만 있거나 빈 문자열이거나 20자를 초과하면 400으로 거부됩니다.",
            example = "홍길동", types = {"string", "null"})
    private String name;

    @Schema(description = "변경할 이메일. null이면 기존 값을 유지합니다.", example = "user@example.com", types = {"string", "null"})
    private String email;

    @Schema(description = "변경할 거주 지역 코드. null이면 기존 값을 유지합니다. 허용되지 않은 코드(빈 문자열 포함)는 400으로 거부됩니다.",
            example = "SEOUL",
            allowableValues = {"SEOUL", "BUSAN", "DAEGU", "INCHEON", "GWANGJU", "DAEJEON", "ULSAN", "SEJONG",
                    "GYEONGGI", "GANGWON", "CHUNGBUK", "CHUNGNAM", "JEONBUK", "JEONNAM", "GYEONGBUK", "GYEONGNAM", "JEJU"},
            types = {"string", "null"})
    private String region;

    @Schema(description = "변경할 출생연도 (4자리 숫자 문자열). null이면 기존 값을 유지합니다. 앞뒤 공백은 제거되며, "
            + "빈 문자열·4자리 숫자가 아닌 값·1940년 미만·(올해-14)년 초과는 400으로 거부됩니다.",
            example = "2000", types = {"string", "null"})
    private String birthYear;

    @Schema(description = "변경할 고용 형태 코드. null이면 기존 값을 유지합니다. 허용되지 않은 코드는 400으로 거부됩니다.",
            example = "EMPLOYED", allowableValues = {"EMPLOYED", "UNEMPLOYED", "STUDENT", "SELF_EMPLOYED"}, types = {"string", "null"})
    private String employmentType;

    @Schema(description = "변경할 세전 월 소득 구간 코드. null이면 기존 값을 유지합니다. 허용되지 않은 코드(구버전 코드 포함)는 400으로 거부됩니다.",
            example = "300_380", allowableValues = {"UNDER_150", "150_260", "260_300", "300_380", "380_410", "OVER_410"},
            types = {"string", "null"})
    private String incomeBracket;
}
