package kr.fintarget.api.domain.policy.condition;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyCondition;
import kr.fintarget.api.domain.user.code.EmploymentType;
import kr.fintarget.api.domain.user.code.HousingStatus;
import kr.fintarget.api.domain.user.code.IncomeBracket;
import kr.fintarget.api.domain.user.entity.User;

/**
 * 추출한 신청 조건으로 사용자에게 확실히 해당하지 않는 정책을 판정한다 (#220).
 *
 * <p>원칙: 확실히 해당하지 않을 때만 제외한다. 사용자 값이 없거나(null), 정책 조건이 UNKNOWN이거나, 아직 추출하지 않았으면 통과시킨다.
 * <ul>
 *   <li>소득: 본인 소득 기준(INDIVIDUAL)이고 상한이 있으며, 사용자 소득 구간의 하한조차 상한을 넘을 때만 제외.
 *       구간 하한 = 상한이면 통과. 부부합산·중위소득 비율·수급자 기준은 가구 정보가 없어 단정할 수 없으므로 제외하지 않는다
 *       (policy-qa-v3의 중위소득 방침과 같음)</li>
 *   <li>주택: 무주택 필수(HOMELESS_REQUIRED) 정책 × 유주택(HOMEOWNER) 사용자만 제외</li>
 *   <li>대상: 사업자 전용(BUSINESS) 정책 × 직업 유형이 자영업자(SELF_EMPLOYED)가 아닌 사용자만 제외. 직업 유형을 모르면 통과</li>
 * </ul>
 */
public final class PolicyConditionMatcher {

    private PolicyConditionMatcher() {
    }

    /** 사용자에게 확실히 해당하지 않는 정책이면 true */
    public static boolean excludes(Policy policy, User user) {
        PolicyCondition condition = policy.getCondition();
        if (condition == null) {
            return false;
        }
        return excludedByIncome(condition, IncomeBracket.fromIncome(user.getIncome()))
                || excludedByHousing(condition, HousingStatus.fromCode(user.getHousingStatus()))
                || excludedByTarget(condition, EmploymentType.fromCode(user.getEmploymentType()));
    }

    static boolean excludedByIncome(PolicyCondition condition, IncomeBracket bracket) {
        return condition.getIncomeBasis() == IncomeBasis.INDIVIDUAL
                && condition.getIncomeMaxMonthly() != null
                && bracket != null
                && bracket.getMinIncome() > condition.getIncomeMaxMonthly();
    }

    static boolean excludedByHousing(PolicyCondition condition, HousingStatus housing) {
        return condition.getHousingRequirement() == HousingRequirement.HOMELESS_REQUIRED
                && housing == HousingStatus.HOMEOWNER;
    }

    static boolean excludedByTarget(PolicyCondition condition, EmploymentType employment) {
        return condition.getTargetType() == TargetType.BUSINESS
                && employment != null
                && employment != EmploymentType.SELF_EMPLOYED;
    }
}
