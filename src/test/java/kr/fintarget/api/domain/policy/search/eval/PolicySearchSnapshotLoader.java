package kr.fintarget.api.domain.policy.search.eval;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.policy.search.eval.InMemoryChunkSearchRepository.IndexedChunk;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchSnapshot.SnapshotChunk;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchSnapshot.SnapshotPolicy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 스냅샷 정책을 H2에 저장하고, 청크를 저장된 정책 ID 기준의 메모리 검색으로 묶는다 (#184).
 *
 * <p>후보 정책 필터는 운영과 같은 코드(PolicyService.findCandidatePolicies → JPQL findMatchingPolicies)를 H2에서 돌린다.
 * H2 저장 시 정책 ID가 새로 생성되므로 청크의 정책 ID를 저장된 ID로 바꿔 넣는다. 평가 결과는 ID 대신 {@link PolicyKey}로 비교한다.
 */
public final class PolicySearchSnapshotLoader {

    private PolicySearchSnapshotLoader() {
    }

    public static Loaded load(PolicySearchSnapshot snapshot, PolicyRepository policyRepository) {
        Map<UUID, Policy> bySnapshotId = new LinkedHashMap<>();
        Map<PolicyKey, Policy> byKey = new LinkedHashMap<>();
        for (SnapshotPolicy row : snapshot.policies()) {
            Policy saved = policyRepository.save(row.toPolicy());
            bySnapshotId.put(row.policyId(), saved);
            if (byKey.put(row.key(), saved) != null) {
                throw new IllegalStateException("스냅샷에 같은 정책 키가 두 번 있습니다: " + row.key());
            }
        }
        policyRepository.flush();

        List<IndexedChunk> chunks = new ArrayList<>(snapshot.chunks().size());
        Map<UUID, PolicyKey> keyById = new HashMap<>();
        for (SnapshotChunk chunk : snapshot.chunks()) {
            Policy policy = bySnapshotId.get(chunk.policyId());
            chunks.add(new IndexedChunk(policy.getPolicyId(), chunk.chunkId(), chunk.sectionLabel(), chunk.content(),
                    chunk.vector()));
        }
        byKey.forEach((key, policy) -> keyById.put(policy.getPolicyId(), key));
        return new Loaded(byKey, keyById, new InMemoryChunkSearchRepository(chunks));
    }

    /**
     * @param policies  정책 키 → H2에 저장된 정책
     * @param keyById   H2 정책 ID → 정책 키 (검색 결과를 평가셋 정답과 비교할 때)
     * @param searchRepository 저장된 정책 ID 기준 메모리 검색
     */
    public record Loaded(Map<PolicyKey, Policy> policies, Map<UUID, PolicyKey> keyById,
                         InMemoryChunkSearchRepository searchRepository) {

        public PolicyKey keyOf(Policy policy) {
            PolicyKey key = keyById.get(policy.getPolicyId());
            if (key == null) {
                throw new IllegalArgumentException("스냅샷에서 적재한 정책이 아닙니다: " + policy.getPolicyId());
            }
            return key;
        }
    }
}
