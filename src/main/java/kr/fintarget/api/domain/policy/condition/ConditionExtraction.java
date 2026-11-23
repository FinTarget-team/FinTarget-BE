package kr.fintarget.api.domain.policy.condition;

import kr.fintarget.api.domain.policy.entity.PolicyCondition;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 정책 1건의 조건 추출 결과 (#220). 해석할 수 없는 필드는 이미 UNKNOWN(지역은 빈 목록)으로 바뀐 값이다.
 *
 * @param incomeMaxMonthly 본인 소득 상한(세전 월, 원). incomeBasis가 INDIVIDUAL일 때만 값이 있다
 * @param regions          원문에서 읽은 지역 코드 (정의된 Region 코드만)
 */
public record ConditionExtraction(IncomeBasis incomeBasis, Long incomeMaxMonthly, HousingRequirement housingRequirement,
                                  TargetType targetType, List<String> regions) {

    public ConditionExtraction {
        regions = regions == null ? List.of() : List.copyOf(regions);
    }

    /** 모든 조건을 모름. 응답 해석 실패나 판단할 원문이 없을 때 저장한다 */
    public static ConditionExtraction unknown() {
        return new ConditionExtraction(IncomeBasis.UNKNOWN, null, HousingRequirement.UNKNOWN, TargetType.UNKNOWN, List.of());
    }

    public PolicyCondition toPolicyCondition(String promptVersion, String sourceHash, LocalDateTime extractedAt) {
        return PolicyCondition.builder()
                .incomeBasis(incomeBasis)
                .incomeMaxMonthly(incomeMaxMonthly)
                .housingRequirement(housingRequirement)
                .targetType(targetType)
                .extractedRegion(regions.isEmpty() ? null : String.join(",", regions))
                .promptVersion(promptVersion)
                .sourceHash(sourceHash)
                .extractedAt(extractedAt)
                .build();
    }
}
