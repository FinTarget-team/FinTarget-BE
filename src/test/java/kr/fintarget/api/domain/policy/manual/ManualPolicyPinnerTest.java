package kr.fintarget.api.domain.policy.manual;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.manual.ManualPolicyCatalog.ManualPolicy;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 직접 등록 정책 고정 노출(#245): 직업 유형별 구성, 공통 후보 조회 통과 여부, 위치·중복·최대 N건, 끄기.
 */
class ManualPolicyPinnerTest {

    private static final String SCHOLARSHIP = "2026 국가장학금 I유형";
    private static final String HOPE = "2026 희망두배 청년통장";
    private static final String FUTURE = "청년미래적금";
    private static final String ALLOWANCE = "2026 서울 청년수당";
    private static final String RENT = "2026 청년 월세 지원";

    private final ManualPolicyCatalog catalog = new ManualPolicyCatalog();
    private final ManualPolicyPinner pinner = new ManualPolicyPinner(catalog, true, 10);

    private static User user(String employment) {
        return User.builder().id("u1").provider(SocialProvider.KAKAO).providerId("p1").region("SEOUL")
                .birthYear(2003).income(1_000_000L).employmentType(employment).build();
    }

    private static Policy withId(Policy policy) {
        ReflectionTestUtils.setField(policy, "policyId", UUID.randomUUID());
        return policy;
    }

    // 직접 등록 정책 4개 (리소스에서 만든 그대로)
    private List<Policy> manualPolicies() {
        return catalog.policies().stream().map(ManualPolicy::toPolicy).map(ManualPolicyPinnerTest::withId).toList();
    }

    // 신청 기간을 보정한 서울 월세 지원 (온통청년 정책)
    private static Policy rent(String externalId, String name) {
        return withId(Policy.create(name, "설명", 19, 39, null, null, PolicyType.HOUSING, "SEOUL", externalId, "YOUTH_CENTER"));
    }

    private static Policy rent() {
        return rent("20261021005400214208", RENT);
    }

    private static List<Policy> regular(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> withId(Policy.create("정책 " + i, "설명", null, null, null, null, PolicyType.HOUSING, null,
                        "YC-" + i, "YOUTH_CENTER")))
                .toList();
    }

    private static List<Policy> concat(List<Policy> a, List<Policy> b) {
        List<Policy> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static List<String> names(List<Policy> policies) {
        return policies.stream().map(Policy::getName).toList();
    }

    @Test
    void 직업_유형별로_고정_정책을_리소스_순서대로_맨_앞에_넣고_항상_N건을_지킨다() {
        List<Policy> manual = manualPolicies();
        List<Policy> selected = regular(10);
        List<Policy> candidates = concat(concat(selected, manual), List.of(rent()));

        List<Policy> student = pinner.pin(user("STUDENT"), candidates, selected);
        assertThat(names(student)).hasSize(10).startsWith(SCHOLARSHIP, HOPE, FUTURE, RENT, "정책 1", "정책 2");
        assertThat(names(student)).doesNotContain(ALLOWANCE, "정책 7", "정책 8", "정책 9", "정책 10");

        assertThat(names(pinner.pin(user("EMPLOYED"), candidates, selected))).hasSize(10).startsWith(HOPE, FUTURE, RENT, "정책 1");
        assertThat(names(pinner.pin(user("UNEMPLOYED"), candidates, selected))).hasSize(10)
                .startsWith(FUTURE, RENT, ALLOWANCE, "정책 1");
        // 고정 대상이 없는 직업 유형은 원래 결과 그대로
        assertThat(pinner.pin(user("SELF_EMPLOYED"), candidates, selected)).isEqualTo(selected);
        assertThat(pinner.pin(user(null), candidates, selected)).isEqualTo(selected);
    }

    @Test
    void 공통_후보_조회를_통과하지_못한_정책은_넣지_않는다() {
        List<Policy> manual = manualPolicies();
        // 서울 외 지역 사용자처럼 서울 정책(희망두배·서울 월세·청년수당)이 후보에 없는 경우
        List<Policy> nationalOnly = manual.stream().filter(p -> p.getRegion() == null).toList();
        List<Policy> selected = regular(10);

        List<Policy> student = pinner.pin(user("STUDENT"), concat(selected, nationalOnly), selected);
        assertThat(names(student)).startsWith(SCHOLARSHIP, FUTURE, "정책 1").doesNotContain(HOPE, RENT);

        List<Policy> jobSeeker = pinner.pin(user("UNEMPLOYED"), concat(selected, nationalOnly), selected);
        assertThat(names(jobSeeker)).startsWith(FUTURE, "정책 1").doesNotContain(ALLOWANCE);
    }

    @Test
    void 동기화_정책은_출처_외부ID_정책명이_모두_같을_때만_고정한다() {
        List<Policy> manual = manualPolicies();
        List<Policy> selected = regular(10);

        // 다른 환경에서 정책번호가 다르거나 이름이 바뀐 경우
        List<Policy> otherId = concat(concat(selected, manual), List.of(rent("20269999999999999999", RENT)));
        List<Policy> renamed = concat(concat(selected, manual), List.of(rent("20261021005400214208", "2027 청년 월세 지원")));

        assertThat(names(pinner.pin(user("STUDENT"), otherId, selected))).startsWith(SCHOLARSHIP, HOPE, FUTURE, "정책 1")
                .doesNotContain(RENT);
        assertThat(names(pinner.pin(user("STUDENT"), renamed, selected))).startsWith(SCHOLARSHIP, HOPE, FUTURE, "정책 1")
                .doesNotContain("2027 청년 월세 지원");
    }

    @Test
    void 고정_정책이_이미_결과에_있으면_중복_없이_고정_자리로_옮긴다() {
        List<Policy> manual = manualPolicies();
        Policy future = manual.stream().filter(p -> p.getName().equals(FUTURE)).findFirst().orElseThrow();
        List<Policy> selected = new ArrayList<>(regular(9));
        selected.add(4, future);

        List<Policy> result = pinner.pin(user("UNEMPLOYED"), concat(selected, manual), selected);

        assertThat(names(result)).hasSize(10).startsWith(FUTURE, ALLOWANCE, "정책 1", "정책 2", "정책 3", "정책 4", "정책 5")
                .doesNotHaveDuplicates();
        assertThat(names(result)).doesNotContain("정책 9");
    }

    @Test
    void 결과가_N건보다_적으면_고정_정책을_더한_만큼만_돌려준다() {
        List<Policy> manual = manualPolicies();
        List<Policy> selected = regular(3);

        assertThat(names(pinner.pin(user("STUDENT"), concat(selected, manual), selected)))
                .containsExactly(SCHOLARSHIP, HOPE, FUTURE, "정책 1", "정책 2", "정책 3");
    }

    @Test
    void 끄면_결과를_그대로_돌려준다() {
        List<Policy> manual = manualPolicies();
        List<Policy> selected = regular(10);
        ManualPolicyPinner off = new ManualPolicyPinner(catalog, false, 10);

        assertThat(off.pin(user("STUDENT"), concat(selected, manual), selected)).isSameAs(selected);
        assertThat(ManualPolicyPinner.disabled().pin(user("STUDENT"), concat(selected, manual), selected)).isSameAs(selected);
    }
}
