package kr.fintarget.api.domain.policy.recommend;

import kr.fintarget.api.domain.policy.condition.HousingRequirement;
import kr.fintarget.api.domain.policy.condition.IncomeBasis;
import kr.fintarget.api.domain.policy.condition.TargetType;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyCondition;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyRecommendationOrderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    private static Policy policy(String name, String region, String applyPeriodCode, String applyPeriod) {
        return Policy.create(name, null, null, null, null, null, PolicyType.ETC, region, name, "YOUTH_CENTER",
                PolicyText.builder().applyPeriodCode(applyPeriodCode).applyPeriod(applyPeriod).build());
    }

    private static List<String> names(List<Policy> policies) {
        return policies.stream().map(Policy::getName).toList();
    }

    @Test
    void 신청_가능_판정_불가_마감_순서로_정렬한다() {
        List<Policy> policies = List.of(
                policy("마감", null, null, "20260101 ~ 20261031"),
                policy("판정 불가", null, null, null),
                policy("상시", null, "0057002", null),
                policy("시작 전", null, null, "20270301 ~ 20270430"),
                policy("기간 안", null, null, "20260101 ~ 20261231"));

        assertThat(names(PolicyRecommendationOrder.sort(policies, "SEOUL", TODAY)))
                .containsExactly("상시", "기간 안", "판정 불가", "마감", "시작 전");
    }

    @Test
    void 같은_신청_순위에서는_사용자_지역_정책이_전국_정책보다_먼저다() {
        Policy extractedLocal = policy("추출 지역 서울", null, "0057002", null);
        extractedLocal.updateCondition(PolicyCondition.builder().extractedRegion("SEOUL").build());
        List<Policy> policies = List.of(
                policy("전국", null, "0057002", null),
                policy("서울", "SEOUL", "0057002", null),
                extractedLocal,
                policy("판정 불가 서울", "SEOUL", null, null));

        assertThat(names(PolicyRecommendationOrder.sort(policies, "SEOUL", TODAY)))
                .containsExactly("서울", "추출 지역 서울", "전국", "판정 불가 서울");
    }

    @Test
    void 같은_순위에서는_조건이_확인된_정책이_먼저이고_나머지는_기존_순서를_유지한다() {
        Policy known = policy("조건 확인", null, "0057002", null);
        known.updateCondition(PolicyCondition.builder().incomeBasis(IncomeBasis.NONE)
                .housingRequirement(HousingRequirement.NONE).targetType(TargetType.INDIVIDUAL).build());
        Policy partly = policy("일부 확인", null, "0057002", null);
        partly.updateCondition(PolicyCondition.builder().incomeBasis(IncomeBasis.UNKNOWN)
                .housingRequirement(HousingRequirement.NONE).targetType(TargetType.INDIVIDUAL).build());
        List<Policy> policies = List.of(
                policy("미추출 A", null, "0057002", null),
                partly,
                policy("미추출 B", null, "0057002", null),
                known);

        assertThat(names(PolicyRecommendationOrder.sort(policies, "SEOUL", TODAY)))
                .containsExactly("조건 확인", "일부 확인", "미추출 A", "미추출 B");
    }

    @Test
    void 사용자_지역이_없으면_지역_순위를_매기지_않는다() {
        List<Policy> policies = List.of(policy("전국", null, "0057002", null), policy("서울", "SEOUL", "0057002", null));

        assertThat(names(PolicyRecommendationOrder.sort(policies, null, TODAY))).containsExactly("전국", "서울");
    }
}
