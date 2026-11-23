package kr.fintarget.api.domain.policy.condition;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyCondition;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.user.code.EmploymentType;
import kr.fintarget.api.domain.user.code.HousingStatus;
import kr.fintarget.api.domain.user.code.IncomeBracket;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyConditionMatcherTest {

    private static PolicyCondition income(IncomeBasis basis, Long maxMonthly) {
        return PolicyCondition.builder().incomeBasis(basis).incomeMaxMonthly(maxMonthly).build();
    }

    private static PolicyCondition housing(HousingRequirement requirement) {
        return PolicyCondition.builder().housingRequirement(requirement).build();
    }

    private static PolicyCondition target(TargetType type) {
        return PolicyCondition.builder().targetType(type).build();
    }

    @Test
    void 본인_소득_기준은_구간_하한이_상한을_넘을_때만_제외한다() {
        // 260~300만원 구간의 하한 260만원
        PolicyCondition equal = income(IncomeBasis.INDIVIDUAL, 2_600_000L);
        PolicyCondition below = income(IncomeBasis.INDIVIDUAL, 2_599_999L);

        assertThat(PolicyConditionMatcher.excludedByIncome(equal, IncomeBracket.RANGE_260_300)).isFalse();
        assertThat(PolicyConditionMatcher.excludedByIncome(below, IncomeBracket.RANGE_260_300)).isTrue();
        // 구간 상한(300만원)이 정책 상한을 넘어도 하한이 넘지 않으면 확실하지 않아 통과
        assertThat(PolicyConditionMatcher.excludedByIncome(income(IncomeBasis.INDIVIDUAL, 2_800_000L),
                IncomeBracket.RANGE_260_300)).isFalse();
        // 150만원 미만 구간은 하한이 0이라 제외되지 않는다
        assertThat(PolicyConditionMatcher.excludedByIncome(income(IncomeBasis.INDIVIDUAL, 300_000L),
                IncomeBracket.UNDER_150)).isFalse();
    }

    @Test
    void 사용자_소득을_모르거나_상한이_없으면_통과한다() {
        assertThat(PolicyConditionMatcher.excludedByIncome(income(IncomeBasis.INDIVIDUAL, 1_000_000L), null)).isFalse();
        assertThat(PolicyConditionMatcher.excludedByIncome(income(IncomeBasis.INDIVIDUAL, null), IncomeBracket.OVER_410))
                .isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = IncomeBasis.class, names = {"COUPLE_COMBINED", "MEDIAN_RATIO", "RECIPIENT", "NONE", "UNKNOWN"})
    void 본인_소득_기준이_아니면_소득으로_제외하지_않는다(IncomeBasis basis) {
        assertThat(PolicyConditionMatcher.excludedByIncome(income(basis, 1_000_000L), IncomeBracket.OVER_410)).isFalse();
        assertThat(PolicyConditionMatcher.excludedByIncome(income(null, 1_000_000L), IncomeBracket.OVER_410)).isFalse();
    }

    @Test
    void 무주택_필수_정책은_유주택_사용자만_제외한다() {
        PolicyCondition required = housing(HousingRequirement.HOMELESS_REQUIRED);
        PolicyCondition none = housing(HousingRequirement.NONE);

        assertThat(PolicyConditionMatcher.excludedByHousing(required, HousingStatus.HOMEOWNER)).isTrue();
        assertThat(PolicyConditionMatcher.excludedByHousing(required, HousingStatus.HOMELESS)).isFalse();
        assertThat(PolicyConditionMatcher.excludedByHousing(none, HousingStatus.HOMEOWNER)).isFalse();
        assertThat(PolicyConditionMatcher.excludedByHousing(none, HousingStatus.HOMELESS)).isFalse();
        // 주택 보유를 모르면 통과
        assertThat(PolicyConditionMatcher.excludedByHousing(required, null)).isFalse();
        assertThat(PolicyConditionMatcher.excludedByHousing(housing(HousingRequirement.UNKNOWN), HousingStatus.HOMEOWNER))
                .isFalse();
    }

    @Test
    void 사업자_전용_정책은_자영업자가_아닌_사용자만_제외한다() {
        PolicyCondition business = target(TargetType.BUSINESS);

        assertThat(PolicyConditionMatcher.excludedByTarget(business, EmploymentType.EMPLOYED)).isTrue();
        assertThat(PolicyConditionMatcher.excludedByTarget(business, EmploymentType.STUDENT)).isTrue();
        assertThat(PolicyConditionMatcher.excludedByTarget(business, EmploymentType.UNEMPLOYED)).isTrue();
        assertThat(PolicyConditionMatcher.excludedByTarget(business, EmploymentType.SELF_EMPLOYED)).isFalse();
        assertThat(PolicyConditionMatcher.excludedByTarget(business, null)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TargetType.class, names = {"INDIVIDUAL", "BOTH", "UNKNOWN"})
    void 사업자_전용이_아니면_직업_유형과_관계없이_통과한다(TargetType type) {
        for (EmploymentType employment : EmploymentType.values()) {
            assertThat(PolicyConditionMatcher.excludedByTarget(target(type), employment)).isFalse();
        }
    }

    @Test
    void 추출하지_않은_정책은_제외하지_않고_조건_중_하나라도_확실히_맞지_않으면_제외한다() {
        User user = User.builder().id("u").provider(SocialProvider.KAKAO).providerId("p")
                .income(IncomeBracket.OVER_410.getIncome()).housingStatus("HOMEOWNER").employmentType("EMPLOYED").build();
        Policy notExtracted = Policy.create("정책", null, null, null, null, null, PolicyType.ETC, null, null, null);
        Policy homelessOnly = Policy.create("정책", null, null, null, null, null, PolicyType.ETC, null, null, null);
        homelessOnly.updateCondition(PolicyCondition.builder()
                .incomeBasis(IncomeBasis.NONE).housingRequirement(HousingRequirement.HOMELESS_REQUIRED)
                .targetType(TargetType.INDIVIDUAL).build());

        assertThat(PolicyConditionMatcher.excludes(notExtracted, user)).isFalse();
        assertThat(PolicyConditionMatcher.excludes(homelessOnly, user)).isTrue();
    }
}
