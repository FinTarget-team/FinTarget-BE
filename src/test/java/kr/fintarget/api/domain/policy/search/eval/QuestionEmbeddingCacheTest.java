package kr.fintarget.api.domain.policy.search.eval;

import kr.fintarget.api.domain.ai.client.AiEmbeddingClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 질문 임베딩 캐시: 없는 질문만 임베딩하고 파일로 다시 읽는다 (#184). 가짜 임베딩 클라이언트만 쓴다.
 */
class QuestionEmbeddingCacheTest {

    private static final String MODEL = "text-embedding-3-small";

    @TempDir
    Path tempDir;

    private static final class CountingClient implements AiEmbeddingClient {
        private final String model;
        private final List<List<String>> calls = new ArrayList<>();

        CountingClient(String model) {
            this.model = model;
        }

        @Override
        public String model() {
            return model;
        }

        @Override
        public List<float[]> embed(List<String> inputs) {
            calls.add(List.copyOf(inputs));
            return inputs.stream().map(input -> new float[]{input.length(), 1f, 0f}).toList();
        }
    }

    @Test
    void 없는_질문만_임베딩해_저장하고_다시_열면_호출하지_않는다() {
        Path file = tempDir.resolve("cache/question-embeddings.json");
        CountingClient live = new CountingClient(MODEL);

        QuestionEmbeddingCache cache = QuestionEmbeddingCache.open(file, MODEL);
        assertThat(cache.fill(List.of("월세 지원", "전세 대출", "월세 지원"), live)).isEqualTo(2);
        assertThat(live.calls).containsExactly(List.of("월세 지원", "전세 대출"));
        assertThat(Files.exists(file)).isTrue();

        QuestionEmbeddingCache reopened = QuestionEmbeddingCache.open(file, MODEL);
        assertThat(reopened.size()).isEqualTo(2);
        assertThat(reopened.dimensions()).isEqualTo(3);
        assertThat(reopened.missing(List.of("월세 지원", "창업 자금"))).containsExactly("창업 자금");
        assertThat(reopened.client().embed(List.of("전세 대출")).getFirst()).containsExactly(5f, 1f, 0f);

        assertThat(reopened.fill(List.of("월세 지원"), live)).isZero();
        assertThat(live.calls).hasSize(1);
    }

    @Test
    void 질문이_많으면_나눠서_요청한다() {
        CountingClient live = new CountingClient(MODEL);
        List<String> questions = IntStream.range(0, QuestionEmbeddingCache.BATCH_SIZE + 5).mapToObj(i -> "질문 " + i).toList();

        QuestionEmbeddingCache.open(tempDir.resolve("c.json"), MODEL).fill(questions, live);

        assertThat(live.calls).extracting(List::size).containsExactly(QuestionEmbeddingCache.BATCH_SIZE, 5);
    }

    @Test
    void 캐시에_없는_질문은_실호출하지_않고_실패한다() {
        QuestionEmbeddingCache cache = QuestionEmbeddingCache.inMemory(MODEL, Map.of("월세", new float[]{1f}));

        assertThatThrownBy(() -> cache.client().embed(List.of("월세", "전세")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("전세");
    }

    @Test
    void 모델이_다른_캐시와_클라이언트는_쓰지_않는다() {
        Path file = tempDir.resolve("c.json");
        QuestionEmbeddingCache.open(file, MODEL).fill(List.of("월세"), new CountingClient(MODEL));

        assertThatThrownBy(() -> QuestionEmbeddingCache.open(file, "other-model"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("모델");
        assertThatThrownBy(() -> QuestionEmbeddingCache.open(tempDir.resolve("d.json"), MODEL)
                .fill(List.of("월세"), new CountingClient("other-model")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("모델");
    }
}
