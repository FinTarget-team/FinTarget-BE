package kr.fintarget.api.domain.policy.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyText;
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
    @Schema(description = "지원 대상 소득 상한(원). null이면 정형화된 소득 상한이 없다는 뜻이며, 소득 제한이 없다는 뜻은 아닙니다. 소득 조건은 incomeCondition을 참고하세요.", example = "30000000", types = {"integer", "null"})
    Long incomeLimit,
    @Schema(description = "지급 혜택 금액(원). 금액으로 환산되지 않는 혜택(현물/서비스 등)이거나 원문에서 금액을 읽지 못하면 null입니다. "
            + "KINFA(서민금융) 정책은 전부 대출 상품이라 항상 null이며, 대출 한도는 supportContent의 \"대출 한도\" 줄에 있습니다. "
            + "지원 내용은 supportContent를 참고하세요.", example = "2400000", types = {"integer", "null"})
    Long benefitAmount,
    SimulationBenefitResponse simulationBenefit,
    @Schema(description = "나이 조건 원문 (현재 KINFA 정책만 값이 있음). 원문이 없으면 null입니다. minAge·maxAge가 null일 때 표시용입니다.", example = "19세 이상 34세 이하", types = {"string", "null"})
    String ageCondition,
    @Schema(description = "소득 조건 원문. 원문이 없으면 null입니다.", example = "중위소득 150% 이하", types = {"string", "null"})
    String incomeCondition,
    @Schema(description = "지원 내용 원문 (KINFA는 대출 한도·금리 등을 \"라벨: 값\" 줄로 묶은 값). 원문이 없으면 null입니다.", example = "월 최대 20만원, 최장 12개월 지원", types = {"string", "null"})
    String supportContent,
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
        // 원문이 없는 정책(테스트 데이터, 원문 저장 전 행)은 text가 null이다
        PolicyText text = policy.getText();
        return new PolicyResponse(
            policy.getPolicyId(),
            policy.getName(),
            policy.getDescription(),
            policy.getMinAge(),
            policy.getMaxAge(),
            policy.getIncomeLimit(),
            policy.getBenefitAmount(),
            SimulationBenefitResponse.from(policy),
            text == null ? null : text.getAgeCondition(),
            text == null ? null : text.getIncomeCondition(),
            text == null ? null : text.getSupportContent(),
            policy.getPolicyType().name(),
            policy.regionCodes()
        );
    }
}
