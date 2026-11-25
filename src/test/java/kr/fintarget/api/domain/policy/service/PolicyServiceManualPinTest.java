package kr.fintarget.api.domain.policy.service;

import kr.fintarget.api.domain.policy.dto.PolicyResponse;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.manual.ManualPolicyCatalog;
import kr.fintarget.api.domain.policy.manual.ManualPolicyCatalog.ManualPolicy;
import kr.fintarget.api.domain.policy.manual.ManualPolicyPinner;
import kr.fintarget.api.domain.simulation.estimate.SimulationSavingEstimator;
import kr.fintarget.api.domain.policy.recommend.PolicyRecommendationSelector;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.policy.repository.UserPolicyRepository;
import kr.fintarget.api.domain.simulation.repository.SimulationRepository;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 추천 목록에서 직접 등록 정책 고정 노출(#245)이 선택기 결과(리랭킹이든 규칙 대체든) 위에 덧입혀지고,
 * 정책 유형 필터 목록에는 적용되지 않는지.
 */
class PolicyServiceManualPinTest {

    private final PolicyRepository policyRepository = mock(PolicyRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PolicyRecommendationSelector selector = mock(PolicyRecommendationSelector.class);
    private final ManualPolicyCatalog catalog = new ManualPolicyCatalog();

    private static Policy withId(Policy policy) {
        ReflectionTestUtils.setField(policy, "policyId", UUID.randomUUID());
        return policy;
    }

    private PolicyService service(boolean pinEnabled) {
        return new PolicyService(policyRepository, mock(UserPolicyRepository.class), userRepository, selector,
                new ManualPolicyPinner(catalog, pinEnabled, 10), mock(SimulationRepository.class),
                SimulationSavingEstimator.disabled());
    }

    private List<Policy> setUp(String employment) {
        User user = User.builder().id("u1").provider(SocialProvider.KAKAO).providerId("p1").region("SEOUL")
                .birthYear(2003).income(1_000_000L).employmentType(employment).build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        List<Policy> regular = IntStream.rangeClosed(1, 12)
                .mapToObj(i -> withId(Policy.create("정책 " + i, "설명", null, null, null, null, PolicyType.HOUSING, null,
                        "YC-" + i, "YOUTH_CENTER")))
                .toList();
        List<Policy> candidates = new ArrayList<>(regular);
        catalog.policies().stream().map(ManualPolicy::toPolicy).map(PolicyServiceManualPinTest::withId).forEach(candidates::add);
        candidates.add(withId(Policy.create("2026 청년 월세 지원", "설명", 19, 39, null, null, PolicyType.HOUSING, "SEOUL",
                "20261021005400214208", "YOUTH_CENTER")));
        when(policyRepository.findMatchingPolicies(any(), any(), any(), any())).thenReturn(candidates);
        return regular;
    }

    private static List<String> names(List<PolicyResponse> responses) {
        return responses.stream().map(PolicyResponse::name).toList();
    }

    @Test
    void 선택기가_돌려준_최종_결과_앞에_고정_정책을_넣고_10건을_지킨다() {
        List<Policy> regular = setUp("STUDENT");
        // 리랭킹이 고른 결과든 규칙 대체 결과든 선택기 결과 그대로 받는다
        when(selector.select(any(), anyList(), any(LocalDate.class))).thenReturn(regular.subList(0, 10));

        List<String> result = names(service(true).getMatchingPolicies("u1", null));

        assertThat(result).hasSize(10)
                .startsWith("2026 국가장학금 I유형", "2026 희망두배 청년통장", "청년미래적금", "2026 청년 월세 지원", "정책 1");
        assertThat(result).doesNotContain("2026 서울 청년수당", "정책 7");
    }

    @Test
    void 끄면_선택기_결과와_같다() {
        List<Policy> regular = setUp("STUDENT");
        when(selector.select(any(), anyList(), any(LocalDate.class))).thenReturn(regular.subList(0, 10));

        assertThat(names(service(false).getMatchingPolicies("u1", null)))
                .containsExactlyElementsOf(regular.subList(0, 10).stream().map(Policy::getName).toList());
    }

    @Test
    void 정책_유형_필터_목록에는_고정_노출하지_않는다() {
        List<Policy> regular = setUp("STUDENT");
        when(selector.selectWithinType(any(), anyList(), any(LocalDate.class))).thenReturn(regular.subList(0, 5));

        assertThat(names(service(true).getMatchingPolicies("u1", "HOUSING")))
                .containsExactly("정책 1", "정책 2", "정책 3", "정책 4", "정책 5");
    }
}
