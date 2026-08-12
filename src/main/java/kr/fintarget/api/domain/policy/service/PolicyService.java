package kr.fintarget.api.domain.policy.service;

import kr.fintarget.api.domain.policy.dto.PolicyResponse;
import kr.fintarget.api.domain.policy.dto.UserPolicyCreateRequest;
import kr.fintarget.api.domain.policy.dto.UserPolicyResponse;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.entity.UserPolicy;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.policy.repository.UserPolicyRepository;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final UserPolicyRepository userPolicyRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<PolicyResponse> getMatchingPolicies(String userId, String policyType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (user.getAge() == null || user.getIncome() == null) {
            return List.of();
        }

        return policyRepository.findMatchingPolicies(user.getAge(), user.getIncome(), parsePolicyType(policyType))
                .stream()
                .map(PolicyResponse::from)
                .collect(Collectors.toList());
    }

    private PolicyType parsePolicyType(String policyType) {
        if (policyType == null || policyType.isBlank()) {
            return null;
        }
        try {
            return PolicyType.valueOf(policyType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 정책 유형입니다: " + policyType);
        }
    }

    @Transactional
    public UserPolicyResponse createUserPolicy(UUID userId, UserPolicyCreateRequest request) {
        Policy policy = policyRepository.findById(request.policyId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 정책입니다."));

        userPolicyRepository.findByUserIdAndPolicyPolicyId(userId, request.policyId())
                .ifPresent(up -> { throw new IllegalStateException("이미 등록된 정책입니다."); });

        UserPolicy userPolicy = new UserPolicy(
                userId,
                policy,
                UserPolicy.UserPolicyStatus.valueOf(request.status())
        );

        return UserPolicyResponse.from(userPolicyRepository.save(userPolicy));
    }

    @Transactional(readOnly = true)
    public List<UserPolicyResponse> getUserPolicies(UUID userId) {
        return userPolicyRepository.findByUserId(userId)
                .stream()
                .map(UserPolicyResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteUserPolicy(UUID userId, UUID userPolicyId) {
        UserPolicy userPolicy = userPolicyRepository.findById(userPolicyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 정책입니다."));

        if (!userPolicy.getUserId().equals(userId)) {
            throw new IllegalStateException("본인의 정책만 삭제할 수 있습니다.");
        }

        userPolicyRepository.delete(userPolicy);
    }
}

