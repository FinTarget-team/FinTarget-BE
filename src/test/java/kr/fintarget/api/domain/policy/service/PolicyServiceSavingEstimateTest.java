package kr.fintarget.api.domain.policy.service;

import kr.fintarget.api.domain.policy.dto.PolicyResponse;
import kr.fintarget.api.domain.policy.dto.UserPolicyCreateRequest;
import kr.fintarget.api.domain.policy.dto.UserPolicyResponse;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.entity.UserPolicy;
import kr.fintarget.api.domain.policy.manual.ManualPolicyCatalog;
import kr.fintarget.api.domain.policy.manual.ManualPolicyPinner;
import kr.fintarget.api.domain.policy.recommend.RuleBasedPolicyRecommendationSelector;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.policy.repository.UserPolicyRepository;
import kr.fintarget.api.domain.simulation.estimate.SimulationSavingEstimator;
import kr.fintarget.api.domain.simulation.repository.SimulationRepository;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 추천 목록·스크랩 응답의 주거 정책 추정 절감액 노출 (#257).
 * expose 설정과 관계없이 추천 순서가 바뀌지 않는지도 확인한다.
 */
class PolicyServiceSavingEstimateTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    // 리소스의 추정 대상 (기숙사형 청년주택, 시세 50% → 62만원의 50% = 31만원)
    private static final String DORM_ID = "20260922005400113506";
    private static final String DORM_NAME = "기숙사형 청년주택 공급";

    private final PolicyRepository policyRepository = mock(PolicyRepository.class);
    private final UserPolicyRepository userPolicyRepository = mock(UserPolicyRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private static Policy withId(Policy policy) {
        ReflectionTestUtils.setField(policy, "policyId", UUID.randomUUID());
        return policy;
    }

    private static Policy dormPolicy() {
        return withId(Policy.create(DORM_NAME, "시세보다 저렴한 기숙사형 임대주택 공급", 19, 34, null, null,
                PolicyType.HOUSING, null, DORM_ID, "YOUTH_CENTER", PolicyText.builder().build()));
    }

    private static Policy otherPolicy(int index) {
        return withId(Policy.create("정책 " + index, "설명", 19, 34, null, null, PolicyType.HOUSING, null,
                "YC-" + index, "YOUTH_CENTER", PolicyText.builder().build()));
    }

    private static User seoulUser() {
        return User.builder().id(USER_ID).provider(SocialProvider.KAKAO).providerId("p1")
                .region("SEOUL").birthYear(2003).income(1_000_000L).build();
    }

    private PolicyService service(boolean expose) {
        SimulationSavingEstimator estimator =
                new SimulationSavingEstimator(new ManualPolicyCatalog(), true, expose,
                        SimulationSavingEstimator.DEFAULT_SEOUL_BASE_RENT,
                        SimulationSavingEstimator.DEFAULT_GYEONGGI_BASE_RENT);
        return new PolicyService(policyRepository, userPolicyRepository, userRepository,
                new RuleBasedPolicyRecommendationSelector(10, List.of()), ManualPolicyPinner.disabled(),
                mock(SimulationRepository.class), estimator);
    }

    private List<Policy> stubCandidates() {
        List<Policy> policies = new java.util.ArrayList<>();
        policies.add(dormPolicy());
        for (int i = 1; i <= 5; i++) {
            policies.add(otherPolicy(i));
        }
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(seoulUser()));
        when(policyRepository.findMatchingPolicies(any(), any(), any(), any())).thenReturn(policies);
        return policies;
    }

    private static PolicyResponse dormOf(List<PolicyResponse> responses) {
        return responses.stream().filter(response -> DORM_NAME.equals(response.name())).findFirst().orElseThrow();
    }

    @Test
    void 노출을_켜면_추천_목록의_주거_정책에_추정_절감액이_들어간다() {
        stubCandidates();

        PolicyResponse dorm = dormOf(service(true).getMatchingPolicies(USER_ID, null));

        assertThat(dorm.simulationBenefit()).isNotNull();
        assertThat(dorm.simulationBenefit().estimated()).isTrue();
        assertThat(dorm.simulationBenefit().monthlyAmount()).isEqualTo(310_000L);
        assertThat(dorm.simulationBenefit().months()).isEqualTo(12);
        assertThat(dorm.simulationBenefit().basis())
                .isEqualTo("입주 시 서울 주요 대학가 원룸 평균 월세 62만원의 50% 수준 임대료로 계산한 예상 절감액");
    }

    @Test
    void 노출을_끄면_추천_목록에는_추정이_들어가지_않는다() {
        stubCandidates();

        PolicyResponse dorm = dormOf(service(false).getMatchingPolicies(USER_ID, null));

        assertThat(dorm.simulationBenefit()).isNull();
    }

    @Test
    void 추정_대상이_아닌_정책은_설정과_무관하게_그대로_null이다() {
        stubCandidates();

        for (boolean expose : new boolean[]{true, false}) {
            assertThat(service(expose).getMatchingPolicies(USER_ID, null))
                    .filteredOn(response -> !DORM_NAME.equals(response.name()))
                    .allSatisfy(response -> assertThat(response.simulationBenefit()).isNull());
        }
    }

    @Test
    void 추천_순서는_노출_설정과_관계없이_같다() {
        stubCandidates();

        List<UUID> exposed = service(true).getMatchingPolicies(USER_ID, null).stream()
                .map(PolicyResponse::policyId).toList();
        List<UUID> hidden = service(false).getMatchingPolicies(USER_ID, null).stream()
                .map(PolicyResponse::policyId).toList();

        assertThat(exposed).containsExactlyElementsOf(hidden);
    }

    @Test
    void 스크랩_목록과_생성_응답에도_추정_절감액이_들어간다() {
        Policy dorm = dormPolicy();
        UserPolicy userPolicy = new UserPolicy(UUID.fromString(USER_ID), dorm, UserPolicy.UserPolicyStatus.INTEREST);
        ReflectionTestUtils.setField(userPolicy, "userPolicyId", UUID.randomUUID());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(seoulUser()));
        when(userPolicyRepository.findByUserId(UUID.fromString(USER_ID))).thenReturn(List.of(userPolicy));
        when(policyRepository.findById(dorm.getPolicyId())).thenReturn(Optional.of(dorm));
        when(userPolicyRepository.findByUserIdAndPolicyPolicyId(any(), any())).thenReturn(Optional.empty());
        when(userPolicyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<UserPolicyResponse> list = service(true).getUserPolicies(UUID.fromString(USER_ID));
        assertThat(list).singleElement().satisfies(response -> {
            assertThat(response.policy().simulationBenefit().estimated()).isTrue();
            assertThat(response.policy().simulationBenefit().monthlyAmount()).isEqualTo(310_000L);
        });

        UserPolicyResponse created = service(true).createUserPolicy(UUID.fromString(USER_ID),
                new UserPolicyCreateRequest(dorm.getPolicyId(), "INTEREST"));
        assertThat(created.policy().simulationBenefit().estimated()).isTrue();
    }

    @Test
    void 노출을_끄면_스크랩_응답에도_추정이_없고_사용자를_읽지_않는다() {
        Policy dorm = dormPolicy();
        UserPolicy userPolicy = new UserPolicy(UUID.fromString(USER_ID), dorm, UserPolicy.UserPolicyStatus.INTEREST);
        ReflectionTestUtils.setField(userPolicy, "userPolicyId", UUID.randomUUID());
        when(userPolicyRepository.findByUserId(UUID.fromString(USER_ID))).thenReturn(List.of(userPolicy));

        List<UserPolicyResponse> list = service(false).getUserPolicies(UUID.fromString(USER_ID));

        assertThat(list).singleElement()
                .satisfies(response -> assertThat(response.policy().simulationBenefit()).isNull());
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).findById(any());
    }

    @Test
    void 경기_사용자에게는_경기_기준_월세로_추정이_들어간다() {
        stubCandidates();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(gyeonggiUser()));

        PolicyResponse dorm = dormOf(service(true).getMatchingPolicies(USER_ID, null));

        assertThat(dorm.simulationBenefit().estimated()).isTrue();
        assertThat(dorm.simulationBenefit().monthlyAmount()).isEqualTo(225_000L);
        assertThat(dorm.simulationBenefit().basis())
                .isEqualTo("입주 시 경기 원룸 월세 약 45만원(민간 집계 추정치)의 50% 수준 임대료로 계산한 예상 절감액");
    }

    @Test
    void 기준_월세가_없는_지역_사용자에게는_추정이_들어가지_않는다() {
        stubCandidates();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder()
                .id(USER_ID).provider(SocialProvider.KAKAO).providerId("p1")
                .region("BUSAN").birthYear(2003).income(1_000_000L).build()));

        PolicyResponse dorm = dormOf(service(true).getMatchingPolicies(USER_ID, null));

        assertThat(dorm.simulationBenefit()).isNull();
    }

    @Test
    void 이전_단일_키_설정은_서울_값으로_쓰인다() {
        // simulation.saving-estimate.base-rent(단일 키)만 있던 환경에서 서울 결과가 그대로여야 한다
        SimulationSavingEstimator legacy = new SimulationSavingEstimator(new ManualPolicyCatalog(), true, true,
                500_000L, SimulationSavingEstimator.DEFAULT_GYEONGGI_BASE_RENT);

        assertThat(legacy.baseRent(kr.fintarget.api.domain.user.code.Region.SEOUL)).isEqualTo(500_000L);
        assertThat(legacy.estimate(dormPolicy(), seoulUser()).orElseThrow().monthlyAmount()).isEqualTo(250_000L);
    }

    private static User gyeonggiUser() {
        return User.builder().id(USER_ID).provider(SocialProvider.KAKAO).providerId("p1")
                .region("GYEONGGI").birthYear(2003).income(1_000_000L).build();
    }

    @Test
    void 추정을_끄면_노출_설정이_켜져_있어도_들어가지_않는다() {
        stubCandidates();
        PolicyService service = new PolicyService(policyRepository, userPolicyRepository, userRepository,
                new RuleBasedPolicyRecommendationSelector(10, List.of()), ManualPolicyPinner.disabled(),
                mock(SimulationRepository.class),
                new SimulationSavingEstimator(new ManualPolicyCatalog(), false, true,
                        SimulationSavingEstimator.DEFAULT_SEOUL_BASE_RENT,
                        SimulationSavingEstimator.DEFAULT_GYEONGGI_BASE_RENT));

        assertThat(dormOf(service.getMatchingPolicies(USER_ID, null)).simulationBenefit()).isNull();
    }
}
