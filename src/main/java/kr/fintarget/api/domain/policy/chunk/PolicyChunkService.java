package kr.fintarget.api.domain.policy.chunk;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 저장된 정책의 검색용 청크를 원문과 맞춘다 (#180). 동기화 직후 자동 실행과 관리자 백필이 공유한다.
 *
 * <ul>
 *   <li>청크가 없거나 source_hash가 새로 만든 값과 다른 정책만 정책 단위로 지우고 다시 만든다 (#162 혜택 추출과 같은 방식)</li>
 *   <li>청크가 없는 정책도 대상이라, 배포 후 첫 실행이 기존 정책 전체의 백필이 된다</li>
 *   <li>외부 호출 없는 계산이라 한 트랜잭션으로 처리한다. 동시 실행은 PolicySyncOrchestrator의 잠금으로 막는다</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyChunkService {

    private final PolicyRepository policyRepository;
    private final PolicyChunkRepository policyChunkRepository;
    private final PolicyChunker policyChunker;

    /** 청크가 없거나 원문·분할 규칙이 바뀐 정책만 다시 만든다 */
    @Transactional
    public PolicyChunkRefreshResult refreshPending() {
        return refresh(false);
    }

    /** 모든 정책의 청크를 다시 만든다 */
    @Transactional
    public PolicyChunkRefreshResult refreshAll() {
        return refresh(true);
    }

    private PolicyChunkRefreshResult refresh(boolean all) {
        long start = System.currentTimeMillis();
        Map<UUID, Set<String>> storedHashes = new HashMap<>();
        for (PolicyChunkRepository.StoredHash stored : policyChunkRepository.findStoredHashes()) {
            storedHashes.computeIfAbsent(stored.getPolicyId(), id -> new HashSet<>()).add(stored.getSourceHash());
        }

        int policyCount = 0;
        int regenerated = 0;
        int skipped = 0;
        int chunkCount = 0;
        for (Policy policy : policyRepository.findAll()) {
            policyCount++;
            PolicyChunks chunks = policyChunker.chunk(policy);
            Set<String> hashes = storedHashes.get(policy.getPolicyId());
            if (!all && hashes != null && hashes.equals(Set.of(chunks.sourceHash()))) {
                skipped++;
                continue;
            }
            if (hashes != null) {
                policyChunkRepository.deleteByPolicyId(policy.getPolicyId());
            }
            policyChunkRepository.saveAll(chunks.toEntities(policy.getPolicyId()));
            regenerated++;
            chunkCount += chunks.chunks().size();
        }
        int orphans = policyChunkRepository.deleteOrphans();

        long elapsedMs = System.currentTimeMillis() - start;
        log.info("정책 청크 갱신 완료 version={} all={} 정책 {}건, 재생성 {}건(청크 {}개), 유지 {}건, 고아 청크 삭제 {}건, 소요 {}ms",
                PolicyChunker.CHUNKER_VERSION, all, policyCount, regenerated, chunkCount, skipped, orphans, elapsedMs);
        return new PolicyChunkRefreshResult(policyCount, regenerated, skipped, chunkCount, orphans, elapsedMs);
    }
}
