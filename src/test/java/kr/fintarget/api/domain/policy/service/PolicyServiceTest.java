package kr.fintarget.api.domain.policy.service;

import kr.fintarget.api.domain.policy.dto.PolicyResponse;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.policy.repository.UserPolicyRepository;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import kr.fintarget.api.domain.user.validation.BirthYearValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Year;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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

    @InjectMocks
    private PolicyService policyService;

    private User userWith(Integer age, Long income) {
        return userWith(age, income, "SEOUL");
    }

    private User userWith(Integer age, Long income, String region) {
        return User.builder()
                .id("user-1")
                .provider("google")
                .providerId("provider-1")
                .age(age)
                .income(income)
                .region(region)
                .build();
    }

    private Policy policy(String name, String region) {
        return Policy.create(name, "설명", 19, 34, null, null, PolicyType.ETC, region, null, null);
    }

    @Test
    void 나이가_null이면_빈_리스트를_반환한다() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWith(null, 3000000L)));

        List<?> result = policyService.getMatchingPolicies("user-1", null);

        assertThat(result).isEmpty();
        verify(policyRepository, never()).findMatchingPolicies(anyInt(), anyInt(), anyLong(), any());
        verify(policyRepository, never()).findAll();
    }

    @Test
    void 소득이_null이면_빈_리스트를_반환한다() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWith(25, null)));

        List<?> result = policyService.getMatchingPolicies("user-1", null);

        assertThat(result).isEmpty();
        verify(policyRepository, never()).findMatchingPolicies(anyInt(), anyInt(), anyLong(), any());
        verify(policyRepository, never()).findAll();
    }

    @Test
    void 나이와_소득이_모두_있으면_매칭_쿼리를_호출한다() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWith(25, 3000000L)));
        Policy policy = Policy.create("청년 정책", "설명", 19, 34, 3000000L, 500000L, PolicyType.ETC, null, null, null);
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
        User user = User.builder().id("user-1").provider("google").providerId("provider-1")
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

        assertThat(result).extracting(PolicyResponse::name)
                .containsExactly("전국 정책", "울산 정책", "경남·울산 정책");
        assertThat(result.get(2).regions()).containsExactly("GYEONGNAM", "ULSAN");
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
}
