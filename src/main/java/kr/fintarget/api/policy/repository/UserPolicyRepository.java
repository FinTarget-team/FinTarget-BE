package kr.fintarget.api.policy.repository;

import kr.fintarget.api.policy.entity.UserPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPolicyRepository extends JpaRepository<UserPolicy, UUID> {
    List<UserPolicy> findByUserId(UUID userId);
    Optional<UserPolicy> findByUserIdAndPolicyPolicyId(UUID userId, UUID policyId);
    void deleteByUserId(UUID userId);
}