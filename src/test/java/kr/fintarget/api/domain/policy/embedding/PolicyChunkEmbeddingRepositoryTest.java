package kr.fintarget.api.domain.policy.embedding;

import kr.fintarget.api.domain.policy.chunk.PolicyChunk;
import kr.fintarget.api.domain.policy.chunk.PolicyChunkRepository;
import kr.fintarget.api.domain.policy.chunk.PolicyChunks;
import kr.fintarget.api.domain.policy.embedding.PolicyChunkEmbeddingRepository.EmbeddingTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 임베딩 대상 조회 SQL을 H2로 확인한다 (#181).
 * H2에는 vector 타입이 없어 embedding 컬럼을 문자열로 둔 테이블로 대신한다. 저장(CAST AS vector)은 서버에서 docs/sql/181_verify.sql로 확인한다.
 */
@DataJpaTest
@Import(PolicyChunkEmbeddingRepository.class)
class PolicyChunkEmbeddingRepositoryTest {

    private static final String MODEL = "text-embedding-3-small";

    @Autowired
    private PolicyChunkEmbeddingRepository repository;

    @Autowired
    private PolicyChunkRepository policyChunkRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createEmbeddingTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS policy_chunk_embedding (
                    chunk_id uuid PRIMARY KEY, embedding varchar(100000) NOT NULL,
                    model varchar(100) NOT NULL, created_at timestamp NOT NULL)
                """);
    }

    private PolicyChunk chunk(String content) {
        return policyChunkRepository.saveAndFlush(PolicyChunk.of(UUID.randomUUID(),
                new PolicyChunks.Draft(0, "전체", content), "0".repeat(64)));
    }

    private void embedded(PolicyChunk chunk, String model) {
        jdbcTemplate.update("INSERT INTO policy_chunk_embedding VALUES (?, '[0]', ?, CURRENT_TIMESTAMP)",
                chunk.getChunkId(), model);
    }

    @Test
    void 임베딩이_없거나_모델이_다른_청크만_대상으로_고른다() {
        PolicyChunk missing = chunk("임베딩 없음");
        PolicyChunk otherModel = chunk("다른 모델");
        PolicyChunk done = chunk("임베딩 완료");
        embedded(otherModel, "old-model");
        embedded(done, MODEL);

        assertThat(repository.findTargets(false, MODEL))
                .extracting(EmbeddingTarget::chunkId)
                .containsExactlyInAnyOrder(missing.getChunkId(), otherModel.getChunkId());
    }

    @Test
    void all이면_임베딩_여부와_관계없이_전체_청크를_본문과_함께_고른다() {
        PolicyChunk missing = chunk("임베딩 없음");
        PolicyChunk done = chunk("임베딩 완료");
        embedded(done, MODEL);

        assertThat(repository.findTargets(true, MODEL))
                .containsExactlyInAnyOrder(
                        new EmbeddingTarget(missing.getChunkId(), "임베딩 없음"),
                        new EmbeddingTarget(done.getChunkId(), "임베딩 완료"));
    }

    @Test
    void 벡터를_pgvector_입력_형식으로_바꾼다() {
        assertThat(PolicyChunkEmbeddingRepository.toVectorLiteral(new float[]{0.5f, -1f, 1.0E-5f}))
                .isEqualTo("[0.5,-1.0,1.0E-5]");
        assertThat(PolicyChunkEmbeddingRepository.toVectorLiteral(new float[]{})).isEqualTo("[]");
    }
}
