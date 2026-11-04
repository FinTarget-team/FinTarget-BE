package kr.fintarget.api.domain.policy.search;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import static kr.fintarget.api.domain.policy.embedding.PolicyChunkEmbeddingRepository.toVectorLiteral;

/**
 * 정책 청크 벡터 검색 (#182). policy_chunk_embedding에 vector 타입이 있어 JPA 엔티티 없이 네이티브 쿼리로 다룬다 (#181과 같은 이유).
 *
 * <p>정책별 묶기·유사도 하한·k 적용은 SQL이 아니라 {@link PolicySearchService}에서 한다.
 * DISTINCT ON이 PostgreSQL 전용이라 H2 테스트에서 돌지 않기 때문이고, #184가 k·하한을 조정할 때
 * 그 로직이 테스트로 덮여 있는 편이 낫다. 이 쿼리는 후보 정책 안에서 거리순 상위 청크만 가져온다.
 */
@Repository
@RequiredArgsConstructor
public class PolicyChunkSearchRepository {

    // policy_id = ANY(?)로 후보를 좁힌다. IN (?,?,...)로 자리표시자를 동적 생성하면 후보가 수백 건일 때
    // SQL이 매번 달라져 PreparedStatement 캐시가 깨진다 (현재 정책 883건 규모)
    private static final String SEARCH = """
            SELECT c.policy_id, c.chunk_id, c.section_label, c.content,
                   e.embedding <=> CAST(? AS vector) AS distance
            FROM policy_chunk c
            JOIN policy_chunk_embedding e ON e.chunk_id = c.chunk_id
            WHERE c.policy_id = ANY(?)
            ORDER BY distance
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 후보 정책 안에서 질문 벡터에 가까운 청크를 거리순으로 가져온다.
     *
     * @param policyIds 사용자 조건에 맞는 정책 ID. 비어 있으면 조회하지 않고 빈 목록
     * @param limit     가져올 청크 수. 한 정책이 청크 여러 개를 차지하므로 원하는 정책 수보다 넉넉해야 한다
     */
    public List<ChunkMatch> search(List<UUID> policyIds, float[] questionVector, int limit) {
        if (policyIds.isEmpty()) {
            return List.of();
        }
        String vectorLiteral = toVectorLiteral(questionVector);
        return jdbcTemplate.query(connection -> {
            var ps = connection.prepareStatement(SEARCH);
            ps.setString(1, vectorLiteral);
            ps.setArray(2, connection.createArrayOf("uuid", policyIds.toArray()));
            ps.setInt(3, limit);
            return ps;
        }, (rs, rowNum) -> new ChunkMatch(
                rs.getObject("policy_id", UUID.class),
                rs.getObject("chunk_id", UUID.class),
                rs.getString("section_label"),
                rs.getString("content"),
                rs.getDouble("distance")));
    }

    /**
     * @param distance 코사인 거리 (0에 가까울수록 유사)
     */
    public record ChunkMatch(UUID policyId, UUID chunkId, String sectionLabel, String content, double distance) {
    }
}
