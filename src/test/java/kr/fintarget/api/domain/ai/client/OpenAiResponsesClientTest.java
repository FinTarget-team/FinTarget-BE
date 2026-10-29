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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class OpenAiResponsesClientTest {

    private static final String BASE_URL = "https://api.openai.test/v1";
    private static final String URL = BASE_URL + "/responses";
    private static final String API_KEY = "sk-test-SECRET-KEY-VALUE";
    private static final String PROMPT_SECRET = "프롬프트-본문-비밀";
    private static final String ANSWER_SECRET = "답변-본문-비밀";

    private static final String COMPLETED = """
            {"id":"resp_1","object":"response","status":"completed","model":"gpt-6-luna-2026-09-01",
             "output":[
               {"id":"rs_1","type":"reasoning","summary":[]},
               {"id":"msg_1","type":"message","status":"completed","role":"assistant","content":[
                 {"type":"output_text","text":"%s 첫 문장. ","annotations":[]},
                 {"type":"output_text","text":"둘째 문장.","annotations":[]}]}],
             "incomplete_details":null,
             "usage":{"input_tokens":1200,"input_tokens_details":{"cached_tokens":0,"cache_write_tokens":0},
                      "output_tokens":350,"output_tokens_details":{"reasoning_tokens":120},"total_tokens":1550}}
            """.formatted(ANSWER_SECRET);

    private MockRestServiceServer server;

    private OpenAiResponsesClient client(OpenAiProperties.Model model) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        OpenAiProperties properties = new OpenAiProperties(API_KEY, BASE_URL, model, null, null,
                null, null, null, null, null);
        OpenAiHttpExecutor executor = new OpenAiHttpExecutor(builder.build(), properties);
        ReflectionTestUtils.setField(executor, "sleeper", (OpenAiHttpExecutor.Sleeper) duration -> { });
        return new OpenAiResponsesClient(executor);
    }

    private OpenAiResponsesClient client() {
        return client(null);
    }

    private static AiTextRequest request(AiModelTier tier) {
        return new AiTextRequest(PROMPT_SECRET + " 시스템 프롬프트",
                List.of(AiMessage.user("첫 질문"), AiMessage.assistant("첫 답변"), AiMessage.user("둘째 질문")),
                tier);
    }

    @Test
    void 요청_본문에_모델_instructions_대화_출력상한_추론강도_store_false를_담는다() {
        OpenAiResponsesClient client = client();
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.model").value("gpt-6-luna"))
                .andExpect(jsonPath("$.instructions").value(PROMPT_SECRET + " 시스템 프롬프트"))
                .andExpect(jsonPath("$.input.length()").value(3))
                .andExpect(jsonPath("$.input[0].role").value("user"))
                .andExpect(jsonPath("$.input[0].content").value("첫 질문"))
                .andExpect(jsonPath("$.input[1].role").value("assistant"))
                .andExpect(jsonPath("$.input[2].content").value("둘째 질문"))
                .andExpect(jsonPath("$.max_output_tokens").value(4000))
                .andExpect(jsonPath("$.reasoning.effort").value("low"))
                .andExpect(jsonPath("$.store").value(false))
                .andRespond(withSuccess(COMPLETED, MediaType.APPLICATION_JSON));

        client.generate(request(AiModelTier.DEFAULT));

        server.verify();
    }

    @Test
    void 시스템_프롬프트가_없으면_instructions를_보내지_않는다() {
        OpenAiResponsesClient client = client();
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.instructions").doesNotExist())
                .andRespond(withSuccess(COMPLETED, MediaType.APPLICATION_JSON));

        client.generate(new AiTextRequest(null, List.of(AiMessage.user("질문")), null));

        server.verify();
    }

    @Test
    void PREMIUM은_상위_모델_ID로_호출한다() {
        OpenAiResponsesClient client = client();
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.model").value("gpt-6.1-sol"))
                .andRespond(withSuccess(COMPLETED, MediaType.APPLICATION_JSON));

        client.generate(request(AiModelTier.PREMIUM));

        server.verify();
    }

    @Test
    void 설정한_모델_ID를_쓴다() {
        OpenAiResponsesClient client = client(new OpenAiProperties.Model("custom-default", "custom-premium"));
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.model").value("custom-default"))
                .andRespond(withSuccess(COMPLETED, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.model").value("custom-premium"))
                .andRespond(withSuccess(COMPLETED, MediaType.APPLICATION_JSON));

        client.generate(request(AiModelTier.DEFAULT));
        client.generate(request(AiModelTier.PREMIUM));

        server.verify();
    }

    @Test
    void message의_output_text를_이어_붙이고_토큰_수와_실제_모델명을_반환한다() {
        OpenAiResponsesClient client = client();
        server.expect(requestTo(URL)).andRespond(withSuccess(COMPLETED, MediaType.APPLICATION_JSON));

        AiTextResult result = client.generate(request(AiModelTier.DEFAULT));

        assertThat(result.text()).isEqualTo(ANSWER_SECRET + " 첫 문장. 둘째 문장.");
        assertThat(result.model()).isEqualTo("gpt-6-luna-2026-09-01");
        assertThat(result.inputTokens()).isEqualTo(1200);
        assertThat(result.outputTokens()).isEqualTo(350);
        assertThat(result.reasoningTokens()).isEqualTo(120);
    }

    @Test
    void 성공_로그에는_모델_토큰_소요시간만_있고_프롬프트_답변_키가_없다(CapturedOutput output) {
        OpenAiResponsesClient client = client();
        server.expect(requestTo(URL)).andRespond(withSuccess(COMPLETED, MediaType.APPLICATION_JSON));

        client.generate(request(AiModelTier.DEFAULT));

        assertThat(output.getAll())
                .contains("OpenAI responses 호출 완료", "model=gpt-6-luna-2026-09-01", "inputTokens=1200",
                        "outputTokens=350", "reasoningTokens=120", "elapsedMs=", "attempts=1")
                .doesNotContain(PROMPT_SECRET, ANSWER_SECRET, "첫 질문", API_KEY);
    }

    @Test
    void 출력_상한에_걸린_incomplete_응답은_502이고_토큰_수를_로그에_남긴다(CapturedOutput output) {
        OpenAiResponsesClient client = client();
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withSuccess("""
                {"status":"incomplete","model":"gpt-6-luna","output":[{"type":"reasoning","summary":[]}],
                 "incomplete_details":{"reason":"max_output_tokens"},
                 "usage":{"input_tokens":1200,"output_tokens":4000,"output_tokens_details":{"reasoning_tokens":4000}}}
                """, MediaType.APPLICATION_JSON));

        Throwable thrown = catchThrowable(() -> client.generate(request(AiModelTier.DEFAULT)));

        assertThat(thrown).isInstanceOf(AiCallFailedException.class).hasMessage(AiCallFailedException.MESSAGE);
        assertThat(((AiClientException) thrown).getReason()).isEqualTo(Reason.INCOMPLETE);
        assertThat(output.getAll()).contains("incompleteReason=max_output_tokens", "outputTokens=4000");
        server.verify();
    }

    @Test
    void 답변_텍스트가_없으면_502() {
        OpenAiResponsesClient client = client();
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"status":"completed","model":"gpt-6-luna","output":[{"type":"reasoning","summary":[]}],
                 "usage":{"input_tokens":10,"output_tokens":5}}
                """, MediaType.APPLICATION_JSON));

        Throwable thrown = catchThrowable(() -> client.generate(request(AiModelTier.DEFAULT)));

        assertThat(thrown).isInstanceOf(AiCallFailedException.class);
        assertThat(((AiClientException) thrown).getReason()).isEqualTo(Reason.INVALID_RESPONSE);
    }

    @Test
    void status가_failed면_502() {
        OpenAiResponsesClient client = client();
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"status":"failed","model":"gpt-6-luna","output":[]}
                """, MediaType.APPLICATION_JSON));

        Throwable thrown = catchThrowable(() -> client.generate(request(AiModelTier.DEFAULT)));

        assertThat(((AiClientException) thrown).getReason()).isEqualTo(Reason.INVALID_RESPONSE);
    }

    @Test
    void 대화_메시지가_비어_있으면_요청을_만들_수_없다() {
        assertThat(catchThrowable(() -> new AiTextRequest("p", List.of(), null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new AiTextRequest("p", List.of(AiMessage.user("q")), null).tier()).isEqualTo(AiModelTier.DEFAULT);
    }
}
