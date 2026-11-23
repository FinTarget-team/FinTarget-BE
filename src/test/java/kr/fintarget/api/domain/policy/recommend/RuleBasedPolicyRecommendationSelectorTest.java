package kr.fintarget.api.domain.policy.recommend;

import kr.fintarget.api.domain.policy.condition.BenefitKind;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyCondition;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedPolicyRecommendationSelectorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);
    private static final User USER = User.builder().id("u").provider(SocialProvider.KAKAO).providerId("p").region("SEOUL").build();
    private static final List<String> KEYWORDS = List.of("상담", "교육", "행사", "토크");

    private static RuleBasedPolicyRecommendationSelector selector(int limit) {
        return new RuleBasedPolicyRecommendationSelector(limit, KEYWORDS);
    }

    private static Policy policy(String name, BenefitKind kind) {
        return policy(name, kind, null, "0057002");
    }

    // 정책 ID를 이름으로 정해 같은 순위에서의 마지막 정렬(정책 ID)을 예측할 수 있게 한다
    private static Policy policy(String name, BenefitKind kind, String region, String applyPeriodCode) {
        Policy policy = Policy.create(name, null, null, null, null, null, PolicyType.FINANCE_WELFARE, region, name,
                "YOUTH_CENTER", PolicyText.builder().applyPeriodCode(applyPeriodCode).build());
        if (kind != null) {
            policy.updateCondition(PolicyCondition.builder().benefitKind(kind).build());
        }
        ReflectionTestUtils.setField(policy, "policyId", UUID.nameUUIDFromBytes(name.getBytes()));
        return policy;
    }

    private static List<Policy> money(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> policy("현금 정책 " + i, BenefitKind.MONEY)).toList();
    }

    private static List<String> names(List<Policy> policies) {
        return policies.stream().map(Policy::getName).toList();
    }

    @Test
    void 후보가_N보다_많으면_N건만_같으면_전부_적으면_있는_만큼_없으면_빈_목록이다() {
        assertThat(selector(10).select(USER, money(15), TODAY)).hasSize(10);
        assertThat(selector(10).select(USER, money(10), TODAY)).hasSize(10);
        assertThat(selector(10).select(USER, money(3), TODAY)).hasSize(3);
        assertThat(selector(10).select(USER, List.of(), TODAY)).isEmpty();
    }

    @Test
    void 정책명에_제외_키워드가_있으면_경제적_혜택이_있어도_뺀다() {
        List<Policy> candidates = List.of(
                policy("청년 재무상담 지원금", BenefitKind.MONEY),
                policy("금융 교육 바우처", BenefitKind.COST_SAVING),
                policy("청년 월세 지원", BenefitKind.MONEY));

        assertThat(names(selector(10).select(USER, candidates, TODAY))).containsExactly("청년 월세 지원");
        assertThat(selector(10).hasExcludedKeyword("청년 토크콘서트")).isTrue();
        assertThat(selector(10).hasExcludedKeyword(null)).isFalse();
    }

    @Test
    void 비금전_개인_혜택_아님_UNKNOWN_추출_전_정책은_모자라도_넣지_않는다() {
        List<Policy> candidates = List.of(
                policy("청년 공간 운영", BenefitKind.NON_FINANCIAL),
                policy("시설 조성", BenefitKind.NOT_FOR_INDIVIDUAL),
                policy("알 수 없는 정책", BenefitKind.UNKNOWN),
                policy("추출 전 정책", null),
                policy("월세 지원", BenefitKind.MONEY),
                policy("문화 바우처", BenefitKind.COST_SAVING));

        assertThat(names(selector(10).select(USER, candidates, TODAY))).containsExactlyInAnyOrder("월세 지원", "문화 바우처");
    }

    @Test
    void 경제적_혜택_정책으로_못_채울_때만_대출로_채운다() {
        List<Policy> candidates = new ArrayList<>();
        candidates.add(policy("전세 대출", BenefitKind.LOAN));
        candidates.addAll(money(2));

        List<String> result = names(selector(3).select(USER, candidates, TODAY));
        assertThat(result.subList(0, 2)).containsExactlyInAnyOrder("현금 정책 1", "현금 정책 2");
        assertThat(result.get(2)).isEqualTo("전세 대출");
        // 경제적 혜택 정책으로 다 채우면 대출은 넣지 않는다
        assertThat(names(selector(2).select(USER, candidates, TODAY))).doesNotContain("전세 대출");
    }

    @Test
    void 같은_후보_순위에서는_정렬_키_현금_혜택_마감_임박_정책_ID_순서다() {
        Policy closedLocal = policy("마감 서울", BenefitKind.MONEY, "SEOUL", "0057003");
        Policy alwaysNational = policy("상시 전국", BenefitKind.MONEY, null, "0057002");
        Policy alwaysLocal = policy("상시 서울", BenefitKind.MONEY, "SEOUL", "0057002");
        Policy dueSoon = Policy.create("곧 마감 서울", null, null, null, null, null, PolicyType.FINANCE_WELFARE, "SEOUL", "d1",
                "YOUTH_CENTER", PolicyText.builder().applyPeriod("20261101 ~ 20261130").build());
        dueSoon.updateCondition(PolicyCondition.builder().benefitKind(BenefitKind.MONEY).build());
        ReflectionTestUtils.setField(dueSoon, "policyId", UUID.nameUUIDFromBytes("d1".getBytes()));
        Policy dueLater = Policy.create("나중 마감 서울", null, null, null, null, null, PolicyType.FINANCE_WELFARE, "SEOUL", "d2",
                "YOUTH_CENTER", PolicyText.builder().applyPeriod("20261101 ~ 20261231").build());
        dueLater.updateCondition(PolicyCondition.builder().benefitKind(BenefitKind.MONEY).build());
        ReflectionTestUtils.setField(dueLater, "policyId", UUID.nameUUIDFromBytes("d2".getBytes()));

        List<Policy> result = selector(10).select(USER,
                List.of(closedLocal, alwaysNational, dueLater, alwaysLocal, dueSoon), TODAY);

        // 신청 가능·서울 정책 안에서는 마감일이 있는 정책(가까운 순)이 상시 정책보다 먼저, 그다음 전국, 마지막에 마감
        assertThat(names(result)).containsExactly("곧 마감 서울", "나중 마감 서울", "상시 서울", "상시 전국", "마감 서울");
    }

    @Test
    void 이름이_비슷한_정책은_2건까지만_넣고_빈자리는_다른_정책으로_채운다() {
        List<Policy> candidates = List.of(
                policy("생활안정자금 융자(부모요양비)", BenefitKind.MONEY),
                policy("생활안정자금 융자(혼례비)", BenefitKind.MONEY),
                policy("생활안정자금 융자(장례비)", BenefitKind.MONEY),
                policy("2025년 중구 소상공인 경영안정자금 지원 특례보증", BenefitKind.MONEY),
                policy("청년 월세 지원", BenefitKind.MONEY));

        List<String> result = names(selector(4).select(USER, candidates, TODAY));

        assertThat(result).hasSize(4).contains("청년 월세 지원", "2025년 중구 소상공인 경영안정자금 지원 특례보증");
        assertThat(result.stream().filter(name -> name.startsWith("생활안정자금")).count()).isEqualTo(2);
    }

    @Test
    void 다른_정책으로_못_채우면_비슷한_이름_제한을_풀고_정렬_순서대로_채운다() {
        List<Policy> candidates = List.of(
                policy("생활안정자금 융자(부모요양비)", BenefitKind.MONEY),
                policy("생활안정자금 융자(혼례비)", BenefitKind.MONEY),
                policy("생활안정자금 융자(장례비)", BenefitKind.MONEY));

        assertThat(selector(10).select(USER, candidates, TODAY)).hasSize(3);
    }

    @Test
    void 같은_입력이면_입력_순서와_관계없이_항상_같은_결과다() {
        List<Policy> candidates = money(12);
        List<Policy> reversed = new ArrayList<>(candidates);
        Collections.reverse(reversed);

        List<Policy> first = selector(5).select(USER, candidates, TODAY);

        assertThat(selector(5).select(USER, candidates, TODAY)).isEqualTo(first);
        assertThat(selector(5).select(USER, reversed, TODAY)).isEqualTo(first);
    }

    @Test
    void 정책_유형_필터_요청도_같은_규칙으로_그_유형_안에서_상위_N이다() {
        // 유형 필터는 후보 조회에서 이미 걸러져 들어온다. 선택 규칙은 같다
        List<Policy> housingOnly = List.of(
                policy("주거 상담", BenefitKind.MONEY),
                policy("월세 지원", BenefitKind.MONEY),
                policy("공공임대", BenefitKind.COST_SAVING),
                policy("전세 대출", BenefitKind.LOAN));

        assertThat(names(selector(2).select(USER, housingOnly, TODAY))).containsExactlyInAnyOrder("월세 지원", "공공임대");
    }

    private static Policy scoped(String name, String region, Boolean localScope) {
        Policy policy = policy(name, BenefitKind.MONEY, region, "0057002");
        policy.updateCondition(PolicyCondition.builder().benefitKind(BenefitKind.MONEY).localScope(localScope).build());
        return policy;
    }

    @Test
    void 시군구_한정_정책은_사용자_지역_정책이어도_전국_정책보다_뒤에_둔다() {
        // 서울 사용자에게 강남구 한정 정책(localScope=true)은 서울 정책으로 잡히지만, 시·도 전체·전국 정책보다 뒤로 간다
        Policy gangnamOnly = scoped("강남구 청년 월세", "SEOUL", true);
        Policy seoulWide = scoped("서울 청년 월세", "SEOUL", false);
        Policy national = scoped("전국 청년 월세", null, false);
        Policy unknownScope = scoped("범위 모름 청년 수당", null, null);

        List<String> result = names(selector(10).select(USER, List.of(gangnamOnly, national, unknownScope, seoulWide), TODAY));

        assertThat(result.getFirst()).isEqualTo("서울 청년 월세");
        assertThat(result.subList(1, 3)).containsExactlyInAnyOrder("전국 청년 월세", "범위 모름 청년 수당");
        assertThat(result.getLast()).isEqualTo("강남구 청년 월세");
    }

    @Test
    void 시군구_한정이어도_신청_가능_여부가_먼저이고_제외하지는_않는다() {
        Policy openLocal = scoped("강남구 상시 지원금", "SEOUL", true);
        Policy closedNational = policy("마감 전국 지원금", BenefitKind.MONEY, null, "0057003");

        assertThat(names(selector(10).select(USER, List.of(closedNational, openLocal), TODAY)))
                .containsExactly("강남구 상시 지원금", "마감 전국 지원금");
    }
}
