package kr.fintarget.api.domain.policy.service;

import kr.fintarget.api.domain.policy.dto.PolicyResponse;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.policy.repository.UserPolicyRepository;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        return User.builder()
                .id("user-1")
                .provider("google")
                .providerId("provider-1")
                .age(age)
                .income(income)
                .build();
    }

    @Test
    void 나이가_null이면_빈_리스트를_반환한다() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWith(null, 3000000L)));

        List<?> result = policyService.getMatchingPolicies("user-1", null);

        assertThat(result).isEmpty();
        verify(policyRepository, never()).findMatchingPolicies(anyInt(), anyLong(), any());
        verify(policyRepository, never()).findAll();
    }

    @Test
    void 소득이_null이면_빈_리스트를_반환한다() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWith(25, null)));

        List<?> result = policyService.getMatchingPolicies("user-1", null);

        assertThat(result).isEmpty();
        verify(policyRepository, never()).findMatchingPolicies(anyInt(), anyLong(), any());
        verify(policyRepository, never()).findAll();
    }

    @Test
    void 나이와_소득이_모두_있으면_매칭_쿼리를_호출한다() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWith(25, 3000000L)));
        Policy policy = Policy.create("청년 정책", "설명", 19, 34, 3000000L, 500000L, "GENERAL", "전국");
        when(policyRepository.findMatchingPolicies(25, 3000000L, null)).thenReturn(List.of(policy));

        List<PolicyResponse> result = policyService.getMatchingPolicies("user-1", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("청년 정책");
        assertThat(result.get(0).policyType()).isEqualTo("GENERAL");
        verify(policyRepository).findMatchingPolicies(25, 3000000L, null);
        verify(policyRepository, never()).findAll();
    }
}
