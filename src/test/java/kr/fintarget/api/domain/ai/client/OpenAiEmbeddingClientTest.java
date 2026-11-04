package kr.fintarget.api.domain.ai.client;

import kr.fintarget.api.common.config.OpenAiProperties;
import kr.fintarget.api.domain.ai.client.AiClientException.Reason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class OpenAiEmbeddingClientTest {

    private static final String BASE_URL = "https://api.openai.test/v1";
    private static final String URL = BASE_URL + "/embeddings";
    private static final String INPUT_SECRET = "청크-본문-비밀";

    private MockRestServiceServer server;

    private OpenAiEmbeddingClient client(String apiKey, String model) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        OpenAiProperties properties = new OpenAiProperties(apiKey, BASE_URL, null, null, null,
                null, null, null, null, null);
        OpenAiHttpExecutor executor = new OpenAiHttpExecutor(builder.build(), properties);
        ReflectionTestUtils.setField(executor, "sleeper", (OpenAiHttpExecutor.Sleeper) duration -> { });
        return new OpenAiEmbeddingClient(executor, model);
    }

    private OpenAiEmbeddingClient client() {
        return client("sk-test", OpenAiEmbeddingClient.DEFAULT_MODEL);
    }

    // index 순서와 값이 구분되도록 i번째 벡터는 모든 원소가 i+1
    private static String vector(float value, int dimensions) {
        return IntStream.range(0, dimensions).mapToObj(i -> String.valueOf(value))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String response(int... indexes) {
        String data = IntStream.of(indexes)
                .mapToObj(i -> "{\"object\":\"embedding\",\"index\":" + i + ",\"embedding\":"
                        + vector(i + 1, AiEmbeddingClient.DIMENSIONS) + "}")
                .collect(Collectors.joining(","));
        return "{\"object\":\"list\",\"model\":\"text-embedding-3-small\",\"data\":[" + data + "],"
                + "\"usage\":{\"prompt_tokens\":42,\"total_tokens\":42}}";
    }

    @Test
    void 요청에_모델_입력_차원_float_형식을_담고_index_순서대로_돌려준다(CapturedOutput output) {
        OpenAiEmbeddingClient client = client();
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.model").value("text-embedding-3-small"))
                .andExpect(jsonPath("$.input.length()").value(2))
                .andExpect(jsonPath("$.input[0]").value(INPUT_SECRET + " 1"))
                .andExpect(jsonPath("$.input[1]").value("둘째"))
                .andExpect(jsonPath("$.dimensions").value(1536))
                .andExpect(jsonPath("$.encoding_format").value("float"))
                // 응답 data가 index 역순으로 와도 입력 순서로 맞춘다
                .andRespond(withSuccess(response(1, 0), MediaType.APPLICATION_JSON));

        List<float[]> vectors = client.embed(List.of(INPUT_SECRET + " 1", "둘째"));

        server.verify();
        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).hasSize(1536).containsOnly(1f);
        assertThat(vectors.get(1)).hasSize(1536).containsOnly(2f);
        assertThat(output.getAll()).contains("OpenAI embeddings 호출 완료", "inputCount=2", "promptTokens=42")
                .doesNotContain(INPUT_SECRET);
    }

    @Test
    void 빈_입력이면_호출하지_않는다() {
        OpenAiEmbeddingClient client = client();
        server.expect(ExpectedCount.never(), requestTo(URL));

        assertThat(client.embed(List.of())).isEmpty();
        server.verify();
    }

    @Test
    void 응답_개수가_입력과_다르면_INVALID_RESPONSE() {
        OpenAiEmbeddingClient client = client();
        server.expect(requestTo(URL)).andRespond(withSuccess(response(0), MediaType.APPLICATION_JSON));

        Throwable thrown = catchThrowable(() -> client.embed(List.of("a", "b")));

        assertThat(thrown).isInstanceOf(AiCallFailedException.class);
        assertThat(((AiClientException) thrown).getReason()).isEqualTo(Reason.INVALID_RESPONSE);
    }

    @Test
    void index가_중복되면_INVALID_RESPONSE() {
        OpenAiEmbeddingClient client = client();
        server.expect(requestTo(URL)).andRespond(withSuccess(response(0, 0), MediaType.APPLICATION_JSON));

        Throwable thrown = catchThrowable(() -> client.embed(List.of("a", "b")));

        assertThat(((AiClientException) thrown).getReason()).isEqualTo(Reason.INVALID_RESPONSE);
    }

    @Test
    void 차원이_1536이_아니면_INVALID_RESPONSE() {
        OpenAiEmbeddingClient client = client();
        String body = "{\"model\":\"text-embedding-3-small\",\"data\":[{\"index\":0,\"embedding\":"
                + vector(1f, 3) + "}],\"usage\":{\"prompt_tokens\":1}}";
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        Throwable thrown = catchThrowable(() -> client.embed(List.of("a")));

        assertThat(((AiClientException) thrown).getReason()).isEqualTo(Reason.INVALID_RESPONSE);
    }

    @Test
    void 키가_없으면_호출하지_않고_AiUnavailableException() {
        OpenAiEmbeddingClient client = client("", OpenAiEmbeddingClient.DEFAULT_MODEL);
        server.expect(ExpectedCount.never(), requestTo(URL));

        Throwable thrown = catchThrowable(() -> client.embed(List.of("a")));

        assertThat(thrown).isInstanceOf(AiUnavailableException.class);
        assertThat(((AiClientException) thrown).getReason()).isEqualTo(Reason.DISABLED);
        server.verify();
    }

    @Test
    void 모델_설정이_비어_있으면_기본_모델을_쓴다() {
        assertThat(client("sk-test", " ").model()).isEqualTo("text-embedding-3-small");
        assertThat(client("sk-test", "text-embedding-3-large").model()).isEqualTo("text-embedding-3-large");
    }
}
