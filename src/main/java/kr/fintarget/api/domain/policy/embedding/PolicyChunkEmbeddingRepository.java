package kr.fintarget.api.domain.policy.embedding;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * policy_chunk_embedding 네이티브 쿼리 (#181). vector 타입이 H2에 없어 JPA 엔티티를 두지 않는다.
 * 테이블은 docs/sql/181_policy_chunk_embedding.sql로 만든다 (ddl-auto 대상 아님).
 */
@Repository
@RequiredArgsConstructor
public class PolicyChunkEmbeddingRepository {

    private static final String SELECT_ALL = "SELECT c.chunk_id, c.content FROM policy_chunk c ORDER BY c.chunk_id";

    // 임베딩이 없거나 다른 모델로 만든 청크
    private static final String SELECT_PENDING = """
            SELECT c.chunk_id, c.content
            FROM policy_chunk c
            LEFT JOIN policy_chunk_embedding e ON e.chunk_id = c.chunk_id
            WHERE e.chunk_id IS NULL OR e.model <> ?
            ORDER BY c.chunk_id
            """;

    private static final String UPSERT = """
            INSERT INTO policy_chunk_embedding (chunk_id, embedding, model, created_at)
            VALUES (?, CAST(? AS vector), ?, ?)
            ON CONFLICT (chunk_id) DO UPDATE
            SET embedding = EXCLUDED.embedding, model = EXCLUDED.model, created_at = EXCLUDED.created_at
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * @param all   true면 모든 청크
     * @param model 현재 임베딩 모델. all=false일 때 이 모델로 만든 임베딩이 있는 청크는 제외한다
     */
    public List<EmbeddingTarget> findTargets(boolean all, String model) {
        if (all) {
            return jdbcTemplate.query(SELECT_ALL, (rs, rowNum) ->
                    new EmbeddingTarget(rs.getObject("chunk_id", UUID.class), rs.getString("content")));
        }
        return jdbcTemplate.query(SELECT_PENDING, (rs, rowNum) ->
                new EmbeddingTarget(rs.getObject("chunk_id", UUID.class), rs.getString("content")), model);
    }

    /** 청크별 임베딩을 저장하거나 덮어쓴다. 호출하는 쪽 트랜잭션 안에서 실행한다 */
    public void upsertAll(List<ChunkEmbedding> embeddings, String model, LocalDateTime createdAt) {
        Timestamp timestamp = Timestamp.valueOf(createdAt);
        jdbcTemplate.batchUpdate(UPSERT, embeddings, embeddings.size(), (ps, embedding) -> {
            ps.setObject(1, embedding.chunkId());
            ps.setString(2, toVectorLiteral(embedding.vector()));
            ps.setString(3, model);
            ps.setTimestamp(4, timestamp);
        });
    }

    /** pgvector 입력 형식 "[0.1,0.2,...]" */
    static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    public record EmbeddingTarget(UUID chunkId, String content) {
    }

    public record ChunkEmbedding(UUID chunkId, float[] vector) {
    }
}
