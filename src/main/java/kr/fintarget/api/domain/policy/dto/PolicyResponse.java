package kr.fintarget.api.domain.policy.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.fintarget.api.domain.policy.entity.Policy;
import java.util.List;
import java.util.UUID;

public record PolicyResponse(
    @Schema(description = "정책 ID (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID policyId,
    @Schema(description = "정책명", example = "청년월세지원")
    String name,
    @Schema(description = "정책 상세 설명", example = "무주택 청년의 월세 부담을 덜어주기 위한 지원 정책입니다.")
    String description,
    @Schema(description = "지원 대상 최소 나이. null이면 나이 하한 제한이 없습니다.", example = "19", types = {"integer", "null"})
    Integer minAge,
    @Schema(description = "지원 대상 최대 나이. null이면 나이 상한 제한이 없습니다.", example = "39", types = {"integer", "null"})
    Integer maxAge,
    @Schema(description = "지원 대상 소득 상한(원). null이면 소득 제한이 없습니다.", example = "30000000", types = {"integer", "null"})
    Long incomeLimit,
    @Schema(description = "지급 혜택 금액(원). 금액으로 환산되지 않는 혜택(현물/서비스 등)이면 null일 수 있습니다.", example = "2400000", types = {"integer", "null"})
    Long benefitAmount,
    @Schema(description = "정책 유형", example = "HOUSING", allowableValues = {"HOUSING", "FINANCE_WELFARE", "STARTUP", "ETC"})
    String policyType,
    @ArraySchema(
        arraySchema = @Schema(description = "신청 가능 지역 코드 목록 (사용자 region과 같은 17개 시/도 코드). 빈 배열이면 전국 정책입니다.",
            example = "[\"GYEONGNAM\", \"ULSAN\"]"),
        schema = @Schema(allowableValues = {"SEOUL", "BUSAN", "DAEGU", "INCHEON", "GWANGJU", "DAEJEON", "ULSAN", "SEJONG",
            "GYEONGGI", "GANGWON", "CHUNGBUK", "CHUNGNAM", "JEONBUK", "JEONNAM", "GYEONGBUK", "GYEONGNAM", "JEJU"})
    )
    List<String> regions
) {
    public static PolicyResponse from(Policy policy) {
        return new PolicyResponse(
            policy.getPolicyId(),
            policy.getName(),
            policy.getDescription(),
            policy.getMinAge(),
            policy.getMaxAge(),
            policy.getIncomeLimit(),
            policy.getBenefitAmount(),
            policy.getPolicyType().name(),
            policy.regionCodes()
        );
    }
}
