package kr.fintarget.api.domain.policy.search.eval;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.fintarget.api.domain.ai.client.AiEmbeddingClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 평가 질문 임베딩 캐시 (#184). 질문을 한 번만 임베딩해 파일에 저장하고, 이후 검색 평가·풀 생성은 이 파일만 읽는다.
 * 같은 질문 벡터로 설정값을 비교해야 결과 차이가 설정값 때문인지 알 수 있고, 실호출 없이 다시 돌릴 수 있다.
 *
 * <p>키는 질문 문장 그대로다 (질문을 고치면 새로 임베딩). 모델이 다른 캐시 파일은 읽지 않는다.
 * 파일 위치는 환경 변수 {@value #ENV_PATH}, 없으면 스냅샷 폴더의 {@value #DEFAULT_FILE} (둘 다 커밋하지 않는 경로).
 */
public final class QuestionEmbeddingCache {

    public static final String ENV_PATH = "AI_EVAL_EMBEDDING_CACHE";
    public static final String DEFAULT_FILE = "question-embeddings.json";
    // OpenAI 임베딩 요청 1회의 입력 수 (API 상한 2048보다 작게)
    static final int BATCH_SIZE = 100;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path file;
    private final String model;
    private final Map<String, float[]> vectors;

    private QuestionEmbeddingCache(Path file, String model, Map<String, float[]> vectors) {
        this.file = file;
        this.model = model;
        this.vectors = vectors;
    }

    /**
     * 파일이 있으면 읽고, 없으면 빈 캐시를 만든다 (저장은 {@link #fill} 때).
     *
     * @throws IllegalStateException 파일의 모델이 다를 때
     */
    public static QuestionEmbeddingCache open(Path file, String model) {
        if (!Files.exists(file)) {
            return new QuestionEmbeddingCache(file, model, new LinkedHashMap<>());
        }
        try {
            CacheFile read = MAPPER.readValue(file.toFile(), CacheFile.class);
            if (!model.equals(read.model())) {
                throw new IllegalStateException("질문 임베딩 캐시의 모델이 다릅니다: " + read.model() + " (기대 " + model + "), " + file);
            }
            Map<String, float[]> vectors = new LinkedHashMap<>();
            read.entries().forEach(entry -> vectors.put(entry.question(), entry.vector()));
            return new QuestionEmbeddingCache(file, model, vectors);
        } catch (IOException e) {
            throw new UncheckedIOException("질문 임베딩 캐시를 읽을 수 없습니다: " + file, e);
        }
    }

    /** 파일 없이 메모리에만 두는 캐시 (단위 테스트용) */
    public static QuestionEmbeddingCache inMemory(String model, Map<String, float[]> vectors) {
        return new QuestionEmbeddingCache(null, model, new LinkedHashMap<>(vectors));
    }

    public String model() {
        return model;
    }

    public Path file() {
        return file;
    }

    public int size() {
        return vectors.size();
    }

    /** 벡터 차원. 비어 있으면 0 */
    public int dimensions() {
        return vectors.values().stream().mapToInt(vector -> vector.length).findFirst().orElse(0);
    }

    /** 캐시에 없는 질문 (중복 제거, 입력 순서) */
    public List<String> missing(Collection<String> questions) {
        return new LinkedHashSet<>(questions).stream().filter(question -> !vectors.containsKey(question)).toList();
    }

    /**
     * 없는 질문만 live로 임베딩해 캐시에 넣고 파일에 저장한다.
     *
     * @return 새로 임베딩한 질문 수
     * @throws IllegalStateException live 모델이 캐시 모델과 다르거나 차원이 섞일 때
     */
    public int fill(Collection<String> questions, AiEmbeddingClient live) {
        List<String> missing = missing(questions);
        if (missing.isEmpty()) {
            return 0;
        }
        if (!model.equals(live.model())) {
            throw new IllegalStateException("임베딩 모델이 캐시·스냅샷과 다릅니다: " + live.model() + " (기대 " + model + ")");
        }
        for (int from = 0; from < missing.size(); from += BATCH_SIZE) {
            List<String> batch = missing.subList(from, Math.min(from + BATCH_SIZE, missing.size()));
            List<float[]> embedded = live.embed(batch);
            for (int i = 0; i < batch.size(); i++) {
                float[] vector = embedded.get(i);
                if (dimensions() != 0 && dimensions() != vector.length) {
                    throw new IllegalStateException("임베딩 차원이 섞입니다: " + vector.length + " != " + dimensions());
                }
                vectors.put(batch.get(i), vector);
            }
        }
        save();
        return missing.size();
    }

    /** 캐시에서만 벡터를 돌려주는 임베딩 클라이언트. 캐시에 없는 질문이면 IllegalStateException (실호출하지 않음) */
    public AiEmbeddingClient client() {
        return new AiEmbeddingClient() {
            @Override
            public String model() {
                return model;
            }

            @Override
            public List<float[]> embed(List<String> inputs) {
                List<float[]> result = new ArrayList<>(inputs.size());
                for (String input : inputs) {
                    float[] vector = vectors.get(input);
                    if (vector == null) {
                        throw new IllegalStateException("질문 임베딩 캐시에 없는 질문입니다: " + input);
                    }
                    result.add(vector);
                }
                return result;
            }
        };
    }

    private void save() {
        if (file == null) {
            return;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            List<Entry> entries = vectors.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList();
            MAPPER.writeValue(file.toFile(), new CacheFile(model, entries));
        } catch (IOException e) {
            throw new UncheckedIOException("질문 임베딩 캐시를 저장할 수 없습니다: " + file, e);
        }
    }

    record CacheFile(String model, List<Entry> entries) {
    }

    record Entry(String question, float[] vector) {
    }
}
