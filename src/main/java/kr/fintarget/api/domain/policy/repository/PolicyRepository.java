package kr.fintarget.api.domain.policy.repository;

import kr.fintarget.api.domain.policy.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

    @Query("SELECT p FROM Policy p WHERE " +
            "(p.minAge IS NULL OR p.minAge <= :age) AND " +
            "(p.maxAge IS NULL OR p.maxAge >= :age) AND " +
            "(p.incomeLimit IS NULL OR p.incomeLimit >= :income) AND " +
            "(:policyType IS NULL OR p.policyType = :policyType)")
    List<Policy> findMatchingPolicies(@Param("age") int age, @Param("income") Long income, @Param("policyType") String policyType);

    Optional<Policy> findByExternalId(String externalId);

    boolean existsByExternalIdAndSource(String externalId, String source);
}
