package kr.fintarget.api.domain.policy.chunk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PolicyChunkRepository extends JpaRepository<PolicyChunk, UUID> {

    List<PolicyChunk> findByPolicyIdOrderBySeqAsc(UUID policyId);

    /** 정책별로 저장된 source_hash. 한 정책의 청크는 같은 해시를 가지므로 보통 정책당 1행이다 */
    @Query("select distinct c.policyId as policyId, c.sourceHash as sourceHash from PolicyChunk c")
    List<StoredHash> findStoredHashes();

    @Modifying(flushAutomatically = true)
    @Query("delete from PolicyChunk c where c.policyId = :policyId")
    int deleteByPolicyId(UUID policyId);

    /** 정책 테이블에 없는 정책의 청크를 지운다 (삭제 경로를 거치지 않고 정책이 사라진 경우 대비) */
    @Modifying(flushAutomatically = true)
    @Query("delete from PolicyChunk c where c.policyId not in (select p.policyId from Policy p)")
    int deleteOrphans();

    interface StoredHash {
        UUID getPolicyId();

        String getSourceHash();
    }
}
