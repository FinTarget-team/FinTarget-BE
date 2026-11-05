package kr.fintarget.api.domain.policy.search.eval;

import kr.fintarget.api.domain.policy.search.PolicyChunkSearchRepository;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 스냅샷 벡터로 운영 검색 쿼리(JdbcPolicyChunkSearchRepository)와 같은 결과를 계산하는 메모리 구현 (#184).
 *
 * <p>운영 쿼리: 후보 정책의 청크 중 임베딩이 있는 것만(JOIN) 코사인 거리(pgvector &lt;=&gt;) 오름차순으로 LIMIT개.
 * HNSW 인덱스가 없어 운영도 전체 비교(정확 검색)이므로 순서가 같다.
 *
 * <ul>
 *   <li>거리: 1 - (a·b) / (|a||b|). double로 계산한다. pgvector는 float 누적이라 1e-6 수준 차이가 날 수 있으며,
 *       이 차이로 순서가 바뀌는 건 거의 같은 거리의 청크끼리뿐이다</li>
 *   <li>노름이 0인 벡터는 pgvector처럼 NaN이며, PostgreSQL 정렬처럼 맨 뒤로 보낸다</li>
 *   <li>동률: PostgreSQL은 ORDER BY distance만으로는 동률 순서를 보장하지 않는다. 평가 재현성을 위해
 *       chunk_id 오름차순(PostgreSQL uuid 정렬과 같은 소문자 16진수 문자열 순서)으로 고정한다</li>
 * </ul>
 */
public class InMemoryChunkSearchRepository implements PolicyChunkSearchRepository {

    private static final Comparator<ChunkMatch> ORDER = Comparator
            .comparingDouble(ChunkMatch::distance) // Double.compare 기준이라 NaN이 가장 뒤
            .thenComparing(match -> match.chunkId().toString());

    private final List<IndexedChunk> chunks;

    /**
     * @param chunks 검색 대상 청크. policyId는 검색에 넘길 정책 ID(H2에 저장된 ID)여야 한다. vector가 null인 청크는 제외한다
     */
    public InMemoryChunkSearchRepository(List<IndexedChunk> chunks) {
        this.chunks = chunks.stream().filter(chunk -> chunk.vector() != null).toList();
    }

    @Override
    public List<ChunkMatch> search(List<UUID> policyIds, float[] questionVector, int limit) {
        if (policyIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> candidates = new HashSet<>(policyIds);
        return chunks.stream()
                .filter(chunk -> candidates.contains(chunk.policyId()))
                .map(chunk -> new ChunkMatch(chunk.policyId(), chunk.chunkId(), chunk.sectionLabel(), chunk.content(),
                        cosineDistance(chunk.vector(), questionVector)))
                .sorted(ORDER)
                .limit(limit)
                .toList();
    }

    /** 검색 대상(임베딩이 있는) 청크 수 */
    public int size() {
        return chunks.size();
    }

    static double cosineDistance(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("벡터 차원이 다릅니다: " + a.length + " != " + b.length);
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return Double.NaN;
        }
        return 1.0 - dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public record IndexedChunk(UUID policyId, UUID chunkId, String sectionLabel, String content, float[] vector) {
    }
}
