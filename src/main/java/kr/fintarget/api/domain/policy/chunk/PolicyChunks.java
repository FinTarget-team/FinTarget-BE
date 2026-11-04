package kr.fintarget.api.domain.policy.chunk;

import java.util.List;
import java.util.UUID;

/**
 * 정책 1건을 분할한 결과 (#180). 저장 전 값이라 policy_id·chunk_id가 없다.
 *
 * @param sourceHash 분할 입력 원문과 분할 규칙의 SHA-256. 저장된 값과 같으면 다시 만들지 않는다
 */
public record PolicyChunks(List<Draft> chunks, String sourceHash) {

    public PolicyChunks {
        chunks = List.copyOf(chunks);
    }

    public List<PolicyChunk> toEntities(UUID policyId) {
        return chunks.stream().map(draft -> PolicyChunk.of(policyId, draft, sourceHash)).toList();
    }

    public record Draft(int seq, String sectionLabel, String content) {
    }
}
