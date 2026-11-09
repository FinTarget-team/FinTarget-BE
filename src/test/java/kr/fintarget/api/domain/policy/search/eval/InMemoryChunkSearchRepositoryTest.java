package kr.fintarget.api.domain.policy.search.eval;

import kr.fintarget.api.domain.policy.search.PolicyChunkSearchRepository.ChunkMatch;
import kr.fintarget.api.domain.policy.search.eval.InMemoryChunkSearchRepository.IndexedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * 메모리 벡터 검색이 운영 쿼리(&lt;=&gt; 오름차순, 후보 필터, LIMIT)와 같은 규칙으로 정렬하는지 (#184).
 */
class InMemoryChunkSearchRepositoryTest {

    private static final UUID A = UUID.fromString("a0000000-0000-0000-0000-000000000000");
    private static final UUID B = UUID.fromString("b0000000-0000-0000-0000-000000000000");
    private static final float[] QUESTION = {1f, 0f};

    private static IndexedChunk chunk(UUID policyId, String chunkId, float... vector) {
        return new IndexedChunk(policyId, UUID.fromString(chunkId), "전체", "본문 " + chunkId, vector);
    }

    @Test
    void 코사인_거리는_1에서_코사인_유사도를_뺀_값이다() {
        assertThat(InMemoryChunkSearchRepository.cosineDistance(new float[]{1, 0}, new float[]{1, 0})).isZero();
        assertThat(InMemoryChunkSearchRepository.cosineDistance(new float[]{1, 0}, new float[]{0, 1})).isEqualTo(1.0);
        assertThat(InMemoryChunkSearchRepository.cosineDistance(new float[]{1, 0}, new float[]{-1, 0})).isEqualTo(2.0);
        // 크기와 무관하다
        assertThat(InMemoryChunkSearchRepository.cosineDistance(new float[]{3, 4}, new float[]{6, 8})).isCloseTo(0.0, within(1e-12));
        assertThat(InMemoryChunkSearchRepository.cosineDistance(new float[]{1, 0}, new float[]{0.8f, 0.6f})).isCloseTo(0.2, within(1e-6));
        assertThatThrownBy(() -> InMemoryChunkSearchRepository.cosineDistance(new float[]{1}, new float[]{1, 0}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 후보_정책의_청크만_거리_오름차순으로_limit개_가져온다() {
        UUID other = UUID.randomUUID();
        InMemoryChunkSearchRepository repository = new InMemoryChunkSearchRepository(List.of(
                chunk(A, "00000000-0000-0000-0000-000000000003", 0, 1),
                chunk(A, "00000000-0000-0000-0000-000000000001", 1, 0),
                chunk(B, "00000000-0000-0000-0000-000000000002", 0.8f, 0.6f),
                chunk(other, "00000000-0000-0000-0000-000000000009", 1, 0)));

        List<ChunkMatch> matches = repository.search(List.of(A, B), QUESTION, 2);

        assertThat(matches).extracting(ChunkMatch::chunkId).extracting(UUID::toString).containsExactly(
                "00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000002");
        assertThat(matches.getFirst().policyId()).isEqualTo(A);
        assertThat(matches.getFirst().content()).isEqualTo("본문 00000000-0000-0000-0000-000000000001");
        assertThat(matches.get(1).distance()).isCloseTo(0.2, within(1e-6));
    }

    // PostgreSQL은 거리가 같으면 순서를 보장하지 않는다. 평가 재현성을 위해 chunk_id 문자열 순서로 고정한다
    @Test
    void 거리가_같으면_chunk_id_오름차순이다() {
        InMemoryChunkSearchRepository repository = new InMemoryChunkSearchRepository(List.of(
                chunk(A, "f0000000-0000-0000-0000-000000000000", 1, 0),
                chunk(B, "10000000-0000-0000-0000-000000000000", 2, 0),
                chunk(A, "80000000-0000-0000-0000-000000000000", 1, 0)));

        assertThat(repository.search(List.of(A, B), QUESTION, 10)).extracting(m -> m.chunkId().toString()).containsExactly(
                "10000000-0000-0000-0000-000000000000",
                "80000000-0000-0000-0000-000000000000",
                "f0000000-0000-0000-0000-000000000000");
    }

    @Test
    void 노름이_0인_벡터는_NaN이며_맨_뒤다() {
        InMemoryChunkSearchRepository repository = new InMemoryChunkSearchRepository(List.of(
                chunk(A, "00000000-0000-0000-0000-000000000001", 0, 0),
                chunk(A, "00000000-0000-0000-0000-000000000002", -1, 0)));

        List<ChunkMatch> matches = repository.search(List.of(A), QUESTION, 10);

        assertThat(matches.getFirst().distance()).isEqualTo(2.0);
        assertThat(matches.get(1).distance()).isNaN();
    }

    @Test
    void 임베딩이_없는_청크는_운영_JOIN처럼_빠지고_후보가_없으면_빈_결과다() {
        InMemoryChunkSearchRepository repository = new InMemoryChunkSearchRepository(List.of(
                chunk(A, "00000000-0000-0000-0000-000000000001", 1, 0),
                new IndexedChunk(A, UUID.randomUUID(), "추가", "임베딩 없음", null)));

        assertThat(repository.size()).isEqualTo(1);
        assertThat(repository.search(List.of(A), QUESTION, 10)).hasSize(1);
        assertThat(repository.search(List.of(), QUESTION, 10)).isEmpty();
    }
}
