package kr.fintarget.api.domain.ai.client;

import kr.fintarget.api.domain.ai.client.AiClientException.Reason;
import kr.fintarget.api.domain.ai.client.dto.OpenAiEmbeddingsRequest;
import kr.fintarget.api.domain.ai.client.dto.OpenAiEmbeddingsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * OpenAI Embeddings API(POST /v1/embeddings) 클라이언트 (#181).
 * 차원은 DIMENSIONS로 고정해 요청하고, 응답 개수·순서·차원이 요청과 맞지 않으면 INVALID_RESPONSE로 실패한다.
 * 로그에는 모델명, 입력 수, 토큰 수, 소요 시간만 남긴다 (입력 텍스트와 벡터는 남기지 않음).
 */
@Slf4j
@Component
public class OpenAiEmbeddingClient implements AiEmbeddingClient {

    public static final String DEFAULT_MODEL = "text-embedding-3-small";
    private static final String OPERATION = "embeddings";
    private static final String PATH = "/embeddings";
    private static final String ENCODING_FORMAT = "float";

    private final OpenAiHttpExecutor executor;
    private final String model;

    OpenAiEmbeddingClient(OpenAiHttpExecutor executor,
                          @Value("${openai.embedding-model:" + DEFAULT_MODEL + "}") String model) {
        this.executor = executor;
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public List<float[]> embed(List<String> inputs) {
        if (inputs.isEmpty()) {
            return List.of();
        }
        OpenAiCallResult<OpenAiEmbeddingsResponse> result = executor.post(OPERATION, PATH,
                new OpenAiEmbeddingsRequest(model, inputs, DIMENSIONS, ENCODING_FORMAT), OpenAiEmbeddingsResponse.class);
        OpenAiEmbeddingsResponse response = result.body();
        int promptTokens = response.usage() == null || response.usage().promptTokens() == null
                ? 0 : response.usage().promptTokens();

        float[][] ordered = order(response, inputs.size());
        if (ordered == null) {
            log.warn("OpenAI {} 응답 이상 model={} inputCount={} dataCount={} elapsedMs={}", OPERATION, model,
                    inputs.size(), response.data() == null ? 0 : response.data().size(), result.elapsedMs());
            throw AiClientException.of(Reason.INVALID_RESPONSE);
        }
        log.info("OpenAI {} 호출 완료 model={} inputCount={} promptTokens={} elapsedMs={} attempts={}",
                OPERATION, model, inputs.size(), promptTokens, result.elapsedMs(), result.attempts());
        return Arrays.asList(ordered);
    }

    // data[].index 순서로 정렬한다. 개수가 다르거나, index가 범위 밖·중복이거나, 차원이 다르면 null
    private static float[][] order(OpenAiEmbeddingsResponse response, int expectedCount) {
        if (response.data() == null || response.data().size() != expectedCount) {
            return null;
        }
        float[][] ordered = new float[expectedCount][];
        for (OpenAiEmbeddingsResponse.Embedding item : response.data()) {
            if (item == null || item.index() == null || item.index() < 0 || item.index() >= expectedCount
                    || ordered[item.index()] != null
                    || item.embedding() == null || item.embedding().length != DIMENSIONS) {
                return null;
            }
            ordered[item.index()] = item.embedding();
        }
        return ordered;
    }
}
