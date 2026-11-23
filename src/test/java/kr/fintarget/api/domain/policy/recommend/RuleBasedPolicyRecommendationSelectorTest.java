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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void 비금전_개인_혜택_아님_UNKNOWN은_모자라도_넣지_않는다() {
        List<Policy> candidates = List.of(
                policy("청년 공간 운영", BenefitKind.NON_FINANCIAL),
                policy("시설 조성", BenefitKind.NOT_FOR_INDIVIDUAL),
                policy("알 수 없는 정책", BenefitKind.UNKNOWN),
                policy("월세 지원", BenefitKind.MONEY),
                policy("문화 바우처", BenefitKind.COST_SAVING));

        assertThat(names(selector(10).select(USER, candidates, TODAY))).containsExactlyInAnyOrder("월세 지원", "문화 바우처");
    }

    // v1으로만 추출해 경제적 혜택 유형이 없는 정책 (배포 직후 상태)
    private static Policy v1Extracted(String name) {
        Policy policy = policy(name, null);
        policy.updateCondition(PolicyCondition.builder().promptVersion("policy-condition-v1").build());
        return policy;
    }

    @Test
    void 미추출_정책만_있으면_혜택_유형_조건만_건너뛰고_채운다() {
        // 배포 직후: 모든 정책이 v1이거나 조건이 없다. 키워드 제외는 그대로 적용한다
        List<Policy> candidates = List.of(
                v1Extracted("청년 월세 지원"),
                policy("조건 없는 정책", null),
                v1Extracted("청년 재무상담"),
                v1Extracted("청년 수당"));

        assertThat(names(selector(10).select(USER, candidates, TODAY)))
                .containsExactlyInAnyOrder("청년 월세 지원", "조건 없는 정책", "청년 수당");
    }

    @Test
    void 추출이_끝난_경제적_혜택_정책_미추출_정책_대출_순서로_채운다() {
        List<Policy> candidates = List.of(
                policy("전세 대출", BenefitKind.LOAN),
                v1Extracted("미추출 정책"),
                policy("알 수 없는 정책", BenefitKind.UNKNOWN),
                policy("월세 지원", BenefitKind.MONEY));

        assertThat(names(selector(10).select(USER, candidates, TODAY)))
                .containsExactly("월세 지원", "미추출 정책", "전세 대출");
        assertThat(names(selector(2).select(USER, candidates, TODAY))).containsExactly("월세 지원", "미추출 정책");
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

    @Test
    void 온통청년_zipCd_정형값이_AI_추출값보다_우선한다() {
        // AI는 한정이 아니라고 했지만(false) zipCd로는 한정(true) → 뒤로. 반대로 AI가 true여도 zipCd가 false면 앞으로
        Policy structuredLocal = scoped("정형값 한정 지원금", "SEOUL", false).withRegionLocalScope(true);
        Policy structuredWide = scoped("정형값 서울 전체 지원금", "SEOUL", true).withRegionLocalScope(false);
        Policy aiOnly = scoped("AI만 한정 지원금", "SEOUL", true);

        assertThat(names(selector(10).select(USER, List.of(structuredLocal, aiOnly, structuredWide), TODAY)).getFirst())
                .isEqualTo("정형값 서울 전체 지원금");
        assertThat(names(selector(10).select(USER, List.of(structuredLocal, structuredWide), TODAY)))
                .containsExactly("정형값 서울 전체 지원금", "정형값 한정 지원금");
    }

    @Test
    void 정책명에_예외_키워드가_있으면_제외_키워드를_적용하지_않는다() {
        RuleBasedPolicyRecommendationSelector exempting =
                new RuleBasedPolicyRecommendationSelector(10, List.of("상담", "교육"), List.of("바우처"));
        List<Policy> candidates = List.of(
                policy("정신건강 심리상담 바우처 사업", BenefitKind.COST_SAVING),
                policy("청년드림은행(청년재무상담소)", BenefitKind.MONEY),
                policy("2026년 청년정책 종합상담 참여자 모집", BenefitKind.COST_SAVING),
                policy("청년 월세 지원", BenefitKind.MONEY));

        assertThat(names(exempting.select(USER, candidates, TODAY)))
                .containsExactlyInAnyOrder("정신건강 심리상담 바우처 사업", "청년 월세 지원");
        assertThat(exempting.hasExcludedKeyword("금융 교육 바우처")).isFalse();
        assertThat(exempting.hasExcludedKeyword("금융 교육")).isTrue();
    }

    // ---- #227 특정 대상 전용 정책 후순위

    private static Policy special(String name, String region, Boolean specialTarget, Boolean localScope) {
        Policy policy = policy(name, BenefitKind.MONEY, region, "0057002");
        policy.updateCondition(PolicyCondition.builder().benefitKind(BenefitKind.MONEY).localScope(localScope)
                .specialTarget(specialTarget).specialTargetGroup(Boolean.TRUE.equals(specialTarget) ? "자립준비청년" : null).build());
        return policy;
    }

    @Test
    void 특정_대상_전용_정책은_사용자_지역_정책이어도_일반_정책보다_뒤에_둔다() {
        // 서울 사용자에게 서울 자립준비청년 정책은 지역 정책이지만, 전국 일반 정책보다 뒤로 간다. 제외하지는 않는다
        Policy seoulSpecial = special("서울 자립정착금", "SEOUL", true, false);
        Policy nationalGeneral = special("전국 청년 수당", null, false, false);
        Policy nationalUnknown = special("판단 불가 청년 지원금", null, null, false);
        Policy seoulGeneral = special("서울 청년 월세", "SEOUL", false, false);

        assertThat(names(selector(10).select(USER, List.of(seoulSpecial, nationalUnknown, nationalGeneral, seoulGeneral), TODAY)))
                .containsExactly("서울 청년 월세", nationalOrder(nationalGeneral, nationalUnknown).get(0),
                        nationalOrder(nationalGeneral, nationalUnknown).get(1), "서울 자립정착금");
    }

    // 같은 순위의 두 정책은 정책 ID 순서다
    private static List<String> nationalOrder(Policy a, Policy b) {
        return String.valueOf(a.getPolicyId()).compareTo(String.valueOf(b.getPolicyId())) < 0
                ? List.of(a.getName(), b.getName()) : List.of(b.getName(), a.getName());
    }

    @Test
    void 특정_대상_전용보다_신청_가능_여부와_시군구_한정_여부가_먼저다() {
        Policy openSpecial = special("상시 자립정착금", null, true, false);
        Policy closedGeneral = policy("마감 청년 수당", BenefitKind.MONEY, null, "0057003");
        Policy localGeneral = special("강남구 청년 수당", "SEOUL", false, true);

        assertThat(names(selector(10).select(USER, List.of(closedGeneral, localGeneral, openSpecial), TODAY)))
                .containsExactly("상시 자립정착금", "강남구 청년 수당", "마감 청년 수당");
    }

    @Test
    void 특정_대상_전용_여부가_모두_비어_있으면_기존과_같은_결과다() {
        // v3 추출 전(specialTarget null)과 모두 일반 정책(false)인 경우는 specialTarget 정렬이 없던 결과와 같다
        List<Policy> withoutField = mixedCandidates(null);
        List<Policy> allFalse = mixedCandidates(false);

        for (int limit : List.of(1, 3, 10)) {
            assertThat(names(selector(limit).select(USER, allFalse, TODAY)))
                    .isEqualTo(names(selector(limit).select(USER, withoutField, TODAY)));
        }
        List<String> result = names(selector(10).select(USER, withoutField, TODAY));
        assertThat(result.subList(0, 2)).containsExactlyInAnyOrder("서울 월세 지원금", "서울 문화 바우처");
        assertThat(result.subList(2, result.size()))
                .containsExactly("전국 청년 수당", "강남구 교통비", "마감 서울 이사비", "미추출 정책", "전세 대출");
    }

    // 지역·신청기간·시·군·구 한정·혜택 유형이 섞인 후보. specialTarget만 인자로 바꾼다
    private static List<Policy> mixedCandidates(Boolean specialTarget) {
        List<Policy> policies = new ArrayList<>();
        policies.add(withCondition("서울 월세 지원금", "SEOUL", "0057002", BenefitKind.MONEY, false, specialTarget));
        policies.add(withCondition("서울 문화 바우처", "SEOUL", "0057002", BenefitKind.COST_SAVING, false, specialTarget));
        policies.add(withCondition("전국 청년 수당", null, "0057002", BenefitKind.MONEY, false, specialTarget));
        policies.add(withCondition("강남구 교통비", "SEOUL", "0057002", BenefitKind.MONEY, true, specialTarget));
        policies.add(withCondition("마감 서울 이사비", "SEOUL", "0057003", BenefitKind.MONEY, false, specialTarget));
        policies.add(withCondition("서울 청년 상담", "SEOUL", "0057002", BenefitKind.MONEY, false, specialTarget));
        policies.add(withCondition("청년 공간", "SEOUL", "0057002", BenefitKind.NON_FINANCIAL, false, specialTarget));
        policies.add(withCondition("전세 대출", null, "0057002", BenefitKind.LOAN, false, specialTarget));
        policies.add(policy("미추출 정책", null));
        return policies;
    }

    private static Policy withCondition(String name, String region, String applyPeriodCode, BenefitKind kind, Boolean localScope,
                                        Boolean specialTarget) {
        Policy policy = policy(name, kind, region, applyPeriodCode);
        policy.updateCondition(PolicyCondition.builder().benefitKind(kind).localScope(localScope).specialTarget(specialTarget).build());
        return policy;
    }

    // ---- #227 리랭킹 후보 목록

    private static RuleBasedPolicyRecommendationSelector rerankSelector(int limit, int candidateLimit) {
        return new RuleBasedPolicyRecommendationSelector(limit, KEYWORDS, List.of(), candidateLimit);
    }

    private static List<Policy> loans(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> policy("대출 상품 " + i, BenefitKind.LOAN)).toList();
    }

    @Test
    void 후보_목록은_최대_건수까지만_돌려주고_적으면_있는_만큼만_돌려준다() {
        assertThat(rerankSelector(10, 50).rerankCandidates(USER, money(60), TODAY)).hasSize(50);
        assertThat(rerankSelector(10, 50).rerankCandidates(USER, money(20), TODAY)).hasSize(20);
        assertThat(rerankSelector(10, 50).rerankCandidates(USER, money(3), TODAY)).hasSize(3);
        assertThat(rerankSelector(10, 50).rerankCandidates(USER, List.of(), TODAY)).isEmpty();
    }

    @Test
    void 경제적_혜택_정책이_N건_이상이면_미추출_정책과_대출은_넣지_않는다() {
        List<Policy> candidates = new ArrayList<>(money(15));
        candidates.add(policy("미추출 정책", null));
        candidates.addAll(loans(20));

        List<Policy> result = rerankSelector(10, 50).rerankCandidates(USER, candidates, TODAY);

        assertThat(result).hasSize(15).allMatch(policy -> policy.getCondition().getBenefitKind() == BenefitKind.MONEY);
    }

    @Test
    void 경제적_혜택_정책이_부족하면_N건을_채울_만큼만_미추출_대출_순서로_보충한다() {
        List<Policy> candidates = new ArrayList<>(money(3));
        candidates.add(policy("미추출 정책 1", null));
        candidates.add(policy("미추출 정책 2", null));
        candidates.addAll(loans(20));

        List<Policy> result = rerankSelector(10, 50).rerankCandidates(USER, candidates, TODAY);

        assertThat(result).hasSize(10);
        assertThat(names(result.subList(0, 3))).allMatch(name -> name.startsWith("현금 정책"));
        assertThat(names(result.subList(3, 5))).containsExactlyInAnyOrder("미추출 정책 1", "미추출 정책 2");
        assertThat(names(result.subList(5, 10))).allMatch(name -> name.startsWith("대출 상품"));
    }

    @Test
    void 후보_목록에_제외_키워드_비금전_개인_혜택_아님_UNKNOWN_정책은_넣지_않는다() {
        List<Policy> candidates = new ArrayList<>(money(2));
        candidates.add(policy("청년 재무상담 지원금", BenefitKind.MONEY));
        candidates.add(policy("청년 공간 운영", BenefitKind.NON_FINANCIAL));
        candidates.add(policy("시설 조성", BenefitKind.NOT_FOR_INDIVIDUAL));
        candidates.add(policy("알 수 없는 정책", BenefitKind.UNKNOWN));

        assertThat(names(rerankSelector(10, 50).rerankCandidates(USER, candidates, TODAY)))
                .containsExactlyInAnyOrder("현금 정책 1", "현금 정책 2");
    }

    @Test
    void 후보_목록의_앞_N건은_상위_N_결과와_같고_뒤는_비슷한_이름_2건_제한을_지킨다() {
        // 지역만 다른 "청년 수당" 6건 + 서로 다른 이름 12건
        List<Policy> candidates = new ArrayList<>();
        for (String region : List.of("서울", "부산", "대구", "인천", "광주", "대전")) {
            candidates.add(policy(region + " 청년 수당", BenefitKind.MONEY));
        }
        IntStream.rangeClosed(1, 12).forEach(i -> candidates.add(policy("다른 지원금 " + (char) ('가' + i), BenefitKind.MONEY)));
        candidates.add(special("자립정착금", null, true, false));

        RuleBasedPolicyRecommendationSelector selector = rerankSelector(10, 50);
        List<Policy> topN = selector.select(USER, candidates, TODAY);
        List<Policy> result = selector.rerankCandidates(USER, candidates, TODAY);

        assertThat(result.subList(0, topN.size())).isEqualTo(topN);
        assertThat(names(result)).filteredOn(name -> name.endsWith("청년 수당")).hasSize(2);
        assertThat(result).hasSize(2 + 12 + 1);
        assertThat(names(result).getLast()).isEqualTo("자립정착금");
    }

    @Test
    void 비슷한_이름_제한을_풀어_채운_상위_N도_후보_목록_앞에_그대로_있다() {
        // 비슷한 이름 5건 + 다른 이름 3건: 상위 N은 제한을 풀어 8건을 모두 넣고, 후보 목록도 같은 8건이다
        List<Policy> candidates = new ArrayList<>();
        for (String region : List.of("서울", "부산", "대구", "인천", "광주")) {
            candidates.add(policy(region + " 청년 수당", BenefitKind.MONEY));
        }
        candidates.addAll(money(3));

        RuleBasedPolicyRecommendationSelector selector = rerankSelector(10, 50);

        assertThat(selector.rerankCandidates(USER, candidates, TODAY)).isEqualTo(selector.select(USER, candidates, TODAY)).hasSize(8);
    }

    @Test
    void 후보_최대_건수는_상위_N_건수보다_작을_수_없다() {
        assertThatThrownBy(() -> rerankSelector(10, 9)).isInstanceOf(IllegalArgumentException.class);
        assertThat(rerankSelector(10, 10).rerankCandidates(USER, money(30), TODAY)).hasSize(10);
    }
}
