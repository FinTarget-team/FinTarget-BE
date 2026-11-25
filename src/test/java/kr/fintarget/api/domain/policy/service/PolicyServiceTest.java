package kr.fintarget.api.domain.policy.service;

import kr.fintarget.api.domain.policy.condition.BenefitKind;
import kr.fintarget.api.domain.policy.condition.HousingRequirement;
import kr.fintarget.api.domain.policy.condition.IncomeBasis;
import kr.fintarget.api.domain.policy.condition.TargetType;
import kr.fintarget.api.domain.policy.dto.PolicyResponse;
import kr.fintarget.api.domain.policy.dto.UserPolicyCreateRequest;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyCondition;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.entity.UserPolicy;
import kr.fintarget.api.domain.policy.recommend.PolicyRecommendationSelector;
import kr.fintarget.api.domain.policy.recommend.RuleBasedPolicyRecommendationSelector;
import kr.fintarget.api.domain.policy.manual.ManualPolicyPinner;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.policy.repository.UserPolicyRepository;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import kr.fintarget.api.domain.user.validation.BirthYearValidator;
import kr.fintarget.api.domain.user.code.IncomeBracket;
import kr.fintarget.api.domain.user.code.SocialProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private UserPolicyRepository userPolicyRepository;

    @Mock
    private UserRepository userRepository;

    // 상위 N 선택(#225)은 실제 규칙 구현을 쓴다 (기본 10건, 제외 키워드 없음)
    @Spy
    private PolicyRecommendationSelector recommendationSelector = new RuleBasedPolicyRecommendationSelector(10, List.of());

    // 직접 등록 정책 고정 노출(#245)은 ManualPolicyPinnerTest에서 검증한다. 여기서는 끈 상태로 기존 동작만 본다
    @Spy
    private ManualPolicyPinner manualPolicyPinner = ManualPolicyPinner.disabled();

    @InjectMocks
    private PolicyService policyService;

    private User userWith(Integer age, Long income) {
        return userWith(age, income, "SEOUL");
    }

    private User userWith(Integer age, Long income, String region) {
        return User.builder()
                .id("user-1")
                .provider(SocialProvider.KAKAO)
                .providerId("provider-1")
                .age(age)
                .income(income)
                .region(region)
                .build();
    }

    private Policy policy(String name, String region) {
        return economic(Policy.create(name, "설명", 19, 34, null, null, PolicyType.ETC, region, null, null));
    }

    // 추천 목록(#225)은 경제적 혜택 정책만 담으므로, 다른 조건을 검증하는 정책은 현금 혜택 정책으로 만든다
    private static Policy economic(Policy policy) {
        policy.updateCondition(PolicyCondition.builder().benefitKind(BenefitKind.MONEY).build());
        return policy;
    }

    // #225: 나이나 소득 중 하나만 없으면 그 조건만 건너뛴다 (null을 넘기면 쿼리가 해당 조건을 건너뜀)
    @Test
    void 나이만_없으면_나이_조건만_건너뛰고_매칭한다() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWith(null, 3000000L)));
        when(policyRepository.findMatchingPolicies(null, null, 3000000L, null)).thenReturn(List.of(policy("정책", null)));

        List<PolicyResponse> result = policyService.getMatchingPolicies("user-1", null);

        assertThat(result).extracting(PolicyResponse::name).containsExactly("정책");
    }

    @Test
    void 소득만_없으면_소득_조건만_건너뛰고_매칭한다() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWith(25, null)));
        when(policyRepository.findMatchingPolicies(25, 25, null, null)).thenReturn(List.of(policy("정책", null)));

        List<PolicyResponse> result = policyService.getMatchingPolicies("user-1", null);

        assertThat(result).extracting(PolicyResponse::name).containsExactly("정책");
    }

    @Test
    void 전체_유형_목록은_select를_정책_유형_필터_목록은_selectWithinType을_쓴다() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWith(25, 3000000L)));
        when(policyRepository.findMatchingPolicies(25, 25, 3000000L, null)).thenReturn(List.of(policy("전체", null)));
        when(policyRepository.findMatchingPolicies(25, 25, 3000000L, PolicyType.HOUSING)).thenReturn(List.of(policy("주거", null)));

        assertThat(policyService.getMatchingPolicies("user-1", null)).extracting(PolicyResponse::name).containsExactly("전체");
        assertThat(policyService.getMatchingPolicies("user-1", "housing")).extracting(PolicyResponse::name).containsExactly("주거");

        verify(recommendationSelector).select(any(), argThat(list -> list.size() == 1 && list.getFirst().getName().equals("전체")), any());
        verify(recommendationSelector).selectWithinType(any(), argThat(list -> list.size() == 1 && list.getFirst().getName().equals("주거")), any());
    }

    @Test
    void 나이와_소득이_모두_없으면_빈_리스트를_반환한다() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWith(null, null)));

        List<?> result = policyService.getMatchingPolicies("user-1", null);

        assertThat(result).isEmpty();
        verify(policyRepository, never()).findMatchingPolicies(any(), any(), any(), any());
        verify(policyRepository, never()).findAll();
    }

    @Test
    void 나이와_소득이_모두_있으면_매칭_쿼리를_호출한다() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWith(25, 3000000L)));
        Policy policy = economic(Policy.create("청년 정책", "설명", 19, 34, 3000000L, 500000L, PolicyType.ETC, null, null, null));
        when(policyRepository.findMatchingPolicies(25, 25, 3000000L, null)).thenReturn(List.of(policy));

        List<PolicyResponse> result = policyService.getMatchingPolicies("user-1", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("청년 정책");
        assertThat(result.get(0).policyType()).isEqualTo("ETC");
        assertThat(result.get(0).regions()).isEmpty();
        verify(policyRepository).findMatchingPolicies(25, 25, 3000000L, null);
        verify(policyRepository, never()).findAll();
    }

    @Test
    void 출생연도가_있으면_생일_전후_두_만_나이를_구간으로_넘긴다() {
        User user = User.builder().id("user-1").provider(SocialProvider.KAKAO).providerId("provider-1")
                .birthYear(2000).income(3000000L).region("SEOUL").build();
        int thisYear = Year.now(BirthYearValidator.ZONE).getValue();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        policyService.getMatchingPolicies("user-1", null);

        verify(policyRepository).findMatchingPolicies(thisYear - 2000 - 1, thisYear - 2000, 3000000L, null);
    }

    @Test
    void 사용자_지역과_일치하는_정책과_전국_정책만_반환한다() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWith(25, 3000000L, "ULSAN")));
        when(policyRepository.findMatchingPolicies(25, 25, 3000000L, null)).thenReturn(List.of(
                policy("전국 정책", null),
                policy("울산 정책", "ULSAN"),
                policy("경남·울산 정책", "GYEONGNAM,ULSAN"),
                policy("서울 정책", "SEOUL"),
                policy("경북 정책", "GYEONGBUK")
        ));

        List<PolicyResponse> result = policyService.getMatchingPolicies("user-1", null);

        // 사용자 지역 정책이 전국 정책보다 먼저 온다 (#220 정렬). 같은 순위끼리는 조회 순서 유지
        assertThat(result).extracting(PolicyResponse::name)
                .containsExactly("울산 정책", "경남·울산 정책", "전국 정책");
        assertThat(result.get(1).regions()).containsExactly("GYEONGNAM", "ULSAN");
    }

    @Test
    void 사용자_지역이_없으면_전국_정책만_반환한다() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWith(25, 3000000L, null)));
        when(policyRepository.findMatchingPolicies(25, 25, 3000000L, null)).thenReturn(List.of(
                policy("전국 정책", null),
                policy("서울 정책", "SEOUL")
        ));

        List<PolicyResponse> result = policyService.getMatchingPolicies("user-1", null);

        assertThat(result).extracting(PolicyResponse::name).containsExactly("전국 정책");
    }

    // 검색(#182)이 쓰는 findCandidatePolicies가 맞춤 정책 목록과 같은 조건을 적용하는지 (분리 전후 동작 동일)
    @Test
    void 조건_필터는_맞춤_정책_목록과_같은_정책을_돌려준다() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWith(25, 3000000L, "ULSAN")));
        when(policyRepository.findMatchingPolicies(25, 25, 3000000L, null)).thenReturn(List.of(
                policy("전국 정책", null),
                policy("울산 정책", "ULSAN"),
                policy("서울 정책", "SEOUL")
        ));

        List<Policy> candidates = policyService.findCandidatePolicies("user-1", null);

        assertThat(candidates).extracting(Policy::getName).containsExactly("전국 정책", "울산 정책");
    }

    @Test
    void 조건_필터도_나이와_소득이_모두_없으면_빈_목록이다() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWith(null, null)));

        assertThat(policyService.findCandidatePolicies("user-1", null)).isEmpty();
        verify(policyRepository, never()).findMatchingPolicies(any(), any(), any(), any());
    }

    @Test
    void 정책_원문의_소득_조건과_지원_내용을_함께_반환한다() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWith(25, 3000000L)));
        PolicyText text = PolicyText.builder()
                .ageCondition("19세 이상 34세 이하")
                .incomeCondition("중위소득 150% 이하")
                .supportContent("대출 한도: 1억원\n금리: 연 2.0%")
                .build();
        Policy policy = economic(Policy.create("원문 있는 정책", "설명", null, null, null, null,
                PolicyType.FINANCE_WELFARE, null, "ext-1", "KINFA", text));
        when(policyRepository.findMatchingPolicies(25, 25, 3000000L, null)).thenReturn(List.of(policy));

        List<PolicyResponse> result = policyService.getMatchingPolicies("user-1", null);

        assertThat(result.get(0).incomeLimit()).isNull();
        assertThat(result.get(0).benefitAmount()).isNull();
        assertThat(result.get(0).ageCondition()).isEqualTo("19세 이상 34세 이하");
        assertThat(result.get(0).incomeCondition()).isEqualTo("중위소득 150% 이하");
        assertThat(result.get(0).supportContent()).isEqualTo("대출 한도: 1억원\n금리: 연 2.0%");
    }

    @Test
    void 정책_원문이_없으면_소득_조건과_지원_내용은_null이다() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWith(25, 3000000L)));
        when(policyRepository.findMatchingPolicies(25, 25, 3000000L, null))
                .thenReturn(List.of(policy("원문 없는 정책", null)));

        List<PolicyResponse> result = policyService.getMatchingPolicies("user-1", null);

        assertThat(result.get(0).ageCondition()).isNull();
        assertThat(result.get(0).incomeCondition()).isNull();
        assertThat(result.get(0).supportContent()).isNull();
    }

    @Test
    void 지역_필터는_정책_유형_필터와_함께_적용된다() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWith(25, 3000000L, "BUSAN")));
        when(policyRepository.findMatchingPolicies(25, 25, 3000000L, PolicyType.HOUSING)).thenReturn(List.of(
                policy("부산 주거", "BUSAN"),
                policy("대구 주거", "DAEGU")
        ));

        List<PolicyResponse> result = policyService.getMatchingPolicies("user-1", "housing");

        assertThat(result).extracting(PolicyResponse::name).containsExactly("부산 주거");
    }

    @Test
    void 내_정책_상태값이_허용값이_아니면_안내_메시지와_함께_예외가_발생한다() {
        UserPolicyCreateRequest request = new UserPolicyCreateRequest(UUID.randomUUID(), "apply");

        assertThatThrownBy(() -> policyService.createUserPolicy(UUID.randomUUID(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 정책 상태입니다. (APPLIED, INTEREST, ABANDONED 중 하나)");

        verify(userPolicyRepository, never()).save(any());
    }

    @Test
    void 내_정책_상태값은_대소문자와_앞뒤_공백을_구분하지_않는다() {
        UUID userId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        when(policyRepository.findById(policyId)).thenReturn(Optional.of(policy("청년 정책", null)));
        when(userPolicyRepository.findByUserIdAndPolicyPolicyId(userId, policyId)).thenReturn(Optional.empty());
        when(userPolicyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        policyService.createUserPolicy(userId, new UserPolicyCreateRequest(policyId, " interest "));

        verify(userPolicyRepository).save(argThat(
                saved -> saved.getStatus() == UserPolicy.UserPolicyStatus.INTEREST));
    }

    @Test
    void 다른_사용자의_내_정책은_없는_정책과_같은_예외로_삭제를_거부한다() {
        UUID userId = UUID.randomUUID();
        UUID othersUserPolicyId = UUID.randomUUID();
        when(userPolicyRepository.findByUserPolicyIdAndUserId(othersUserPolicyId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policyService.deleteUserPolicy(userId, othersUserPolicyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 정책입니다.");

        verify(userPolicyRepository, never()).delete(any());
    }

    // #220: 추출 조건 필터는 기존 지역·나이 필터와 함께 적용되고, 확실히 해당하지 않는 정책만 뺀다
    private User profiledUser(String region, String housing, String employment) {
        return User.builder().id("user-1").provider(SocialProvider.KAKAO).providerId("provider-1")
                .age(25).income(IncomeBracket.RANGE_300_380.getIncome()).region(region)
                .housingStatus(housing).employmentType(employment).build();
    }

    private Policy policyWith(String name, String region, PolicyCondition condition) {
        Policy policy = policy(name, region);
        policy.updateCondition(condition);
        return policy;
    }

    @Test
    void 추출한_조건으로_확실히_해당하지_않는_정책만_빼고_지역_필터와_함께_적용한다() {
        User user = profiledUser("SEOUL", "HOMEOWNER", "EMPLOYED");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(policyRepository.findMatchingPolicies(25, 25, IncomeBracket.RANGE_300_380.getIncome(), null)).thenReturn(List.of(
                policy("추출 전 정책", null),
                policyWith("본인 소득 상한 250만원", null,
                        PolicyCondition.builder().incomeBasis(IncomeBasis.INDIVIDUAL).incomeMaxMonthly(2_500_000L).build()),
                policyWith("본인 소득 상한 300만원(구간 하한과 같음)", null,
                        PolicyCondition.builder().incomeBasis(IncomeBasis.INDIVIDUAL).incomeMaxMonthly(3_000_000L).build()),
                policyWith("중위소득 정책", null, PolicyCondition.builder().incomeBasis(IncomeBasis.MEDIAN_RATIO).build()),
                policyWith("무주택 필수", null,
                        PolicyCondition.builder().housingRequirement(HousingRequirement.HOMELESS_REQUIRED).build()),
                policyWith("사업자 전용", null, PolicyCondition.builder().targetType(TargetType.BUSINESS).build()),
                policyWith("추출 지역 전남", null, PolicyCondition.builder().extractedRegion("JEONNAM").build()),
                policyWith("추출 지역 서울", null, PolicyCondition.builder().extractedRegion("SEOUL").build()),
                policy("부산 정책", "BUSAN")));

        List<Policy> candidates = policyService.findCandidatePolicies("user-1", null);

        assertThat(candidates).extracting(Policy::getName).containsExactly(
                "추출 전 정책", "본인 소득 상한 300만원(구간 하한과 같음)", "중위소득 정책", "추출 지역 서울");
    }

    @Test
    void 직업_유형과_주택_보유를_모르면_대상_주택_조건으로_빼지_않는다() {
        User user = profiledUser("SEOUL", null, null);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(policyRepository.findMatchingPolicies(25, 25, IncomeBracket.RANGE_300_380.getIncome(), null)).thenReturn(List.of(
                policyWith("무주택 필수", null,
                        PolicyCondition.builder().housingRequirement(HousingRequirement.HOMELESS_REQUIRED).build()),
                policyWith("사업자 전용", null, PolicyCondition.builder().targetType(TargetType.BUSINESS).build())));

        assertThat(policyService.findCandidatePolicies("user-1", null)).hasSize(2);
    }

    @Test
    void 맞춤_정책_목록은_신청_가능한_정책과_사용자_지역_정책을_먼저_보여준다() {
        User user = profiledUser("SEOUL", "HOMELESS", "EMPLOYED");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(policyRepository.findMatchingPolicies(25, 25, IncomeBracket.RANGE_300_380.getIncome(), null)).thenReturn(List.of(
                economic(Policy.create("마감 전국", "설명", 19, 34, null, null, PolicyType.ETC, null, null, null,
                        PolicyText.builder().applyPeriod("20200101 ~ 20201231").build())),
                economic(Policy.create("상시 전국", "설명", 19, 34, null, null, PolicyType.ETC, null, null, null,
                        PolicyText.builder().applyPeriodCode("0057002").build())),
                economic(Policy.create("상시 서울", "설명", 19, 34, null, null, PolicyType.ETC, "SEOUL", null, null,
                        PolicyText.builder().applyPeriodCode("0057002").build()))));

        List<PolicyResponse> result = policyService.getMatchingPolicies("user-1", null);

        assertThat(result).extracting(PolicyResponse::name).containsExactly("상시 서울", "상시 전국", "마감 전국");
    }

    @Test
    void 공통_후보_조회는_경제적_혜택_유형과_제외_키워드로_거르지_않는다() {
        // #225 상위 N 선택은 맞춤 목록에만 적용한다. 검색(#182)·통합 답변(#183)이 쓰는 공통 후보는 그대로다
        User user = profiledUser("SEOUL", "HOMELESS", "EMPLOYED");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        Policy consulting = policyWith("청년 재무상담", null, PolicyCondition.builder().benefitKind(BenefitKind.NON_FINANCIAL).build());
        Policy unknown = policyWith("추출 전 정책", null, PolicyCondition.builder().build());
        Policy rent = policyWith("청년 월세 지원", null, PolicyCondition.builder().benefitKind(BenefitKind.MONEY).build());
        when(policyRepository.findMatchingPolicies(25, 25, IncomeBracket.RANGE_300_380.getIncome(), null))
                .thenReturn(List.of(consulting, unknown, rent));

        assertThat(policyService.findCandidatePolicies("user-1", null)).containsExactly(consulting, unknown, rent);
        // 추천 목록에서 비금전 정책은 빠지고, 아직 경제적 혜택 유형을 추출하지 않은 정책은 뒤에 채운다
        assertThat(policyService.getMatchingPolicies("user-1", null)).extracting(PolicyResponse::name)
                .containsExactly("청년 월세 지원", "추출 전 정책");
    }
}
