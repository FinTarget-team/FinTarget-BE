package kr.fintarget.api.domain.policy.repository;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

    // 나이: 사용자 만 나이 구간 [ageLower, ageUpper]와 정책 나이 범위가 겹치면 통과 (User.ageRange 참고)
    // 소득: 사용자 소득 구간의 대표값으로 비교. 정책 incomeLimit은 아직 모두 null이며, 구간 하한·상한 비교는 정책 소득 조건 구조화 이슈에서 바꾼다
    // 지역은 다중 지역 문자열을 코드 단위로 비교해야 해서 조회 후 PolicyService에서 거른다 (Policy.appliesToRegion)
    @Query("SELECT p FROM Policy p WHERE " +
            "(p.minAge IS NULL OR p.minAge <= :ageUpper) AND " +
            "(p.maxAge IS NULL OR p.maxAge >= :ageLower) AND " +
            "(p.incomeLimit IS NULL OR p.incomeLimit >= :income) AND " +
            "(:policyType IS NULL OR p.policyType = :policyType)")
    List<Policy> findMatchingPolicies(@Param("ageLower") int ageLower, @Param("ageUpper") int ageUpper,
                                      @Param("income") Long income, @Param("policyType") PolicyType policyType);

    Optional<Policy> findByExternalId(String externalId);

    boolean existsByExternalIdAndSource(String externalId, String source);

    Optional<Policy> findByExternalIdAndSource(String externalId, String source);

    List<Policy> findBySource(String source);
}
