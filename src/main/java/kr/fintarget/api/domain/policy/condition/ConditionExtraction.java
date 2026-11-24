package kr.fintarget.api.domain.policy.condition;

import kr.fintarget.api.domain.policy.entity.PolicyCondition;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 정책 1건의 조건 추출 결과 (#220). 해석할 수 없는 필드는 이미 UNKNOWN(지역은 빈 목록)으로 바뀐 값이다.
 *
 * @param incomeMaxMonthly 본인 소득 상한(세전 월, 원). incomeBasis가 INDIVIDUAL일 때만 값이 있다
 * @param regions          원문에서 읽은 지역 코드 (정의된 Region 코드만)
 * @param benefitKind      경제적 혜택 유형 (#225)
 * @param localScope       신청 자격이 특정 시·군·구 거주자로 한정되는지 (#225). 알 수 없으면 null
 * @param specialTarget    신청 자격이 특정 집단으로 한정되는지 (#227). 알 수 없으면 null
 * @param specialTargetGroup specialTarget이 true일 때의 대상 집단. true가 아니면 항상 null
 */
public record ConditionExtraction(IncomeBasis incomeBasis, Long incomeMaxMonthly, HousingRequirement housingRequirement,
                                  TargetType targetType, List<String> regions, BenefitKind benefitKind, Boolean localScope,
                                  Boolean specialTarget, String specialTargetGroup) {

    public ConditionExtraction {
        regions = regions == null ? List.of() : List.copyOf(regions);
        benefitKind = benefitKind == null ? BenefitKind.UNKNOWN : benefitKind;
        specialTargetGroup = Boolean.TRUE.equals(specialTarget) ? cleanGroup(specialTargetGroup) : null;
    }

    /** 특정 대상 전용 여부 없이 만든다 (테스트용, null) */
    public ConditionExtraction(IncomeBasis incomeBasis, Long incomeMaxMonthly, HousingRequirement housingRequirement,
                               TargetType targetType, List<String> regions, BenefitKind benefitKind, Boolean localScope) {
        this(incomeBasis, incomeMaxMonthly, housingRequirement, targetType, regions, benefitKind, localScope, null, null);
    }

    // 앞뒤 공백을 지우고 50자(코드 포인트 기준)로 자른다. 비면 null
    private static String cleanGroup(String group) {
        if (group == null || group.isBlank()) {
            return null;
        }
        String trimmed = group.strip();
        int max = PolicyCondition.SPECIAL_TARGET_GROUP_MAX_LENGTH;
        if (trimmed.codePointCount(0, trimmed.length()) <= max) {
            return trimmed;
        }
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, max)).strip();
    }

    /** 시·군·구 한정 여부 없이 만든다 (테스트용, null) */
    public ConditionExtraction(IncomeBasis incomeBasis, Long incomeMaxMonthly, HousingRequirement housingRequirement,
                               TargetType targetType, List<String> regions, BenefitKind benefitKind) {
        this(incomeBasis, incomeMaxMonthly, housingRequirement, targetType, regions, benefitKind, null);
    }

    /** 경제적 혜택 유형 없이 만든다 (테스트용, UNKNOWN) */
    public ConditionExtraction(IncomeBasis incomeBasis, Long incomeMaxMonthly, HousingRequirement housingRequirement,
                               TargetType targetType, List<String> regions) {
        this(incomeBasis, incomeMaxMonthly, housingRequirement, targetType, regions, BenefitKind.UNKNOWN);
    }

    /** 모든 조건을 모름. 응답 해석 실패나 판단할 원문이 없을 때 저장한다 */
    public static ConditionExtraction unknown() {
        return new ConditionExtraction(IncomeBasis.UNKNOWN, null, HousingRequirement.UNKNOWN, TargetType.UNKNOWN, List.of(),
                BenefitKind.UNKNOWN, null, null, null);
    }

    public PolicyCondition toPolicyCondition(String promptVersion, String sourceHash, LocalDateTime extractedAt) {
        return PolicyCondition.builder()
                .incomeBasis(incomeBasis)
                .incomeMaxMonthly(incomeMaxMonthly)
                .housingRequirement(housingRequirement)
                .targetType(targetType)
                .extractedRegion(regions.isEmpty() ? null : String.join(",", regions))
                .benefitKind(benefitKind)
                .localScope(localScope)
                .specialTarget(specialTarget)
                .specialTargetGroup(specialTargetGroup)
                .promptVersion(promptVersion)
                .sourceHash(sourceHash)
                .extractedAt(extractedAt)
                .build();
    }
}
