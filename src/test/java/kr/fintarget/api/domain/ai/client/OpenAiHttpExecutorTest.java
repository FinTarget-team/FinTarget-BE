package kr.fintarget.api.domain.ai.client;

import kr.fintarget.api.common.config.OpenAiProperties;
import kr.fintarget.api.domain.ai.client.AiClientException.Reason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class OpenAiHttpExecutorTest {

    private static final String BASE_URL = "https://api.openai.test/v1";
    private static final String URL = BASE_URL + "/responses";
    private static final String API_KEY = "sk-test-SECRET-KEY-VALUE";
    // 오류 본문의 error.message. 로그와 예외 어디에도 나오면 안 된다
    private static final String SECRET_MESSAGE = "SECRET-ERROR-MESSAGE-정책원문";

    private MockRestServiceServer server;
    private OpenAiHttpExecutor executor;
    private final List<Duration> sleeps = new ArrayList<>();
    private final AtomicLong clockNanos = new AtomicLong();

    @BeforeEach
    void setUp() {
        executor = executorWith(API_KEY);
    }

    private OpenAiHttpExecutor executorWith(String apiKey) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        OpenAiProperties properties = new OpenAiProperties(apiKey, BASE_URL, null, null, null,
                null, null, null, null, null);
        OpenAiHttpExecutor created = new OpenAiHttpExecutor(builder.build(), properties);
        ReflectionTestUtils.setField(created, "sleeper", (OpenAiHttpExecutor.Sleeper) sleeps::add);
        ReflectionTestUtils.setField(created, "nanoTime", (LongSupplier) clockNanos::get);
        return created;
    }

    private OpenAiCallResult<Map> post() {
        return executor.post("responses", "/responses", Map.of("model", "m"), Map.class);
    }

    private static String errorBody(String type, String code) {
        return "{\"error\":{\"message\":\"" + SECRET_MESSAGE + "\",\"type\":\"" + type + "\",\"param\":null,\"code\":"
                + (code == null ? "null" : "\"" + code + "\"") + "}}";
    }

    private static DefaultResponseCreator error(HttpStatus status, String type, String code) {
        return withStatus(status).contentType(MediaType.APPLICATION_JSON).body(errorBody(type, code));
    }

    private static ResponseCreator ok() {
        return withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON);
    }

    private void assertReason(Throwable thrown, Class<? extends AiClientException> type, Reason reason) {
        assertThat(thrown).isInstanceOf(type);
        assertThat(((AiClientException) thrown).getReason()).isEqualTo(reason);
    }

    @Test
    void 성공하면_본문과_시도_횟수를_반환하고_키를_Bearer로_보낸다() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY))
                .andRespond(ok());

        OpenAiCallResult<Map> result = post();

        assertThat(result.body()).containsEntry("ok", true);
        assertThat(result.attempts()).isEqualTo(1);
        server.verify();
    }

    @Test
    void 키가_비어_있으면_HTTP_호출_없이_AiUnavailableException() {
        executor = executorWith("  ");

        Throwable thrown = catchThrowable(this::post);

        assertReason(thrown, AiUnavailableException.class, Reason.DISABLED);
        server.verify(); // 기대한 요청이 없으므로 요청이 0건이어야 통과
    }

    @Test
    void 키가_비어_있으면_생성_시_warn_로그를_한_번_남긴다(CapturedOutput output) {
        executorWith("");

        assertThat(output.getOut().split("AI 기능이 비활성화됩니다", -1)).hasSize(2);
    }

    @Test
    void 호출_한도_429는_재시도하고_성공하면_반환한다() {
        server.expect(requestTo(URL)).andRespond(error(HttpStatus.TOO_MANY_REQUESTS, "requests", "rate_limit_exceeded"));
        server.expect(requestTo(URL)).andRespond(ok());

        OpenAiCallResult<Map> result = post();

        assertThat(result.attempts()).isEqualTo(2);
        assertThat(sleeps).hasSize(1);
        // 기본 백오프 1s ± 20%
        assertThat(sleeps.get(0)).isBetween(Duration.ofMillis(800), Duration.ofMillis(1200));
        server.verify();
    }

    @Test
    void 호출_한도_429가_계속되면_재시도_2회_후_503() {
        server.expect(ExpectedCount.times(3), requestTo(URL))
                .andRespond(error(HttpStatus.TOO_MANY_REQUESTS, "requests", "rate_limit_exceeded"));

        Throwable thrown = catchThrowable(this::post);

        assertReason(thrown, AiUnavailableException.class, Reason.RATE_LIMITED);
        // 지수 백오프: 1s, 2s (± 20%)
        assertThat(sleeps).hasSize(2);
        assertThat(sleeps.get(1)).isBetween(Duration.ofMillis(1600), Duration.ofMillis(2400));
        server.verify();
    }

    @Test
    void 잔액_사용_한도_초과_429는_재시도하지_않고_503() {
        for (String code : List.of("credit_balance_exhausted", "organization_spend_limit_exceeded",
                "project_spend_limit_exceeded", "organization_usage_limit_exceeded")) {
            server.reset();
            sleeps.clear();
            server.expect(ExpectedCount.once(), requestTo(URL))
                    .andRespond(error(HttpStatus.TOO_MANY_REQUESTS, "insufficient_quota", code));

            assertReason(catchThrowable(this::post), AiUnavailableException.class, Reason.QUOTA_EXCEEDED);
            assertThat(sleeps).isEmpty();
            server.verify();
        }
    }

    @Test
    void type이_insufficient_quota면_code가_없어도_재시도하지_않는다() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(error(HttpStatus.TOO_MANY_REQUESTS, "insufficient_quota", null));

        assertReason(catchThrowable(this::post), AiUnavailableException.class, Reason.QUOTA_EXCEEDED);
        server.verify();
    }

    @Test
    void 오류_본문이_JSON이_아닌_429는_호출_한도로_보고_재시도한다() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("<html>busy</html>"));
        server.expect(requestTo(URL)).andRespond(ok());

        assertThat(post().attempts()).isEqualTo(2);
        server.verify();
    }

    @Test
    void OpenAI_503은_재시도_후_503() {
        server.expect(ExpectedCount.times(3), requestTo(URL))
                .andRespond(error(HttpStatus.SERVICE_UNAVAILABLE, "service_unavailable_error", "server_is_overloaded"));

        assertReason(catchThrowable(this::post), AiUnavailableException.class, Reason.OVERLOADED);
        server.verify();
    }

    @Test
    void OpenAI_500은_재시도_후_502() {
        server.expect(ExpectedCount.times(3), requestTo(URL))
                .andRespond(error(HttpStatus.INTERNAL_SERVER_ERROR, "server_error", null));

        assertReason(catchThrowable(this::post), AiCallFailedException.class, Reason.SERVER_ERROR);
        assertThat(sleeps).hasSize(2);
        server.verify();
    }

    @Test
    void OpenAI_502는_재시도하고_성공하면_반환한다() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(URL)).andRespond(ok());

        assertThat(post().attempts()).isEqualTo(2);
        server.verify();
    }

    @Test
    void 요청_오류_400은_재시도하지_않고_502() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(error(HttpStatus.BAD_REQUEST, "invalid_request_error", "unsupported_value"));

        assertReason(catchThrowable(this::post), AiCallFailedException.class, Reason.REQUEST_REJECTED);
        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    void 인증_오류_401_403은_재시도하지_않고_502() {
        for (HttpStatus status : List.of(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)) {
            server.reset();
            server.expect(ExpectedCount.once(), requestTo(URL))
                    .andRespond(error(status, "invalid_request_error", "invalid_api_key"));

            assertReason(catchThrowable(this::post), AiCallFailedException.class, Reason.AUTH_FAILED);
            server.verify();
        }
        assertThat(sleeps).isEmpty();
    }

    @Test
    void Retry_After가_있으면_그만큼_기다린다() {
        server.expect(requestTo(URL)).andRespond(error(HttpStatus.TOO_MANY_REQUESTS, "requests", "rate_limit_exceeded")
                .header(HttpHeaders.RETRY_AFTER, "2"));
        server.expect(requestTo(URL)).andRespond(ok());

        post();

        assertThat(sleeps).containsExactly(Duration.ofSeconds(2));
        server.verify();
    }

    @Test
    void Retry_After가_5초보다_길면_재시도하지_않는다() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(error(HttpStatus.TOO_MANY_REQUESTS, "requests", "rate_limit_exceeded")
                        .header(HttpHeaders.RETRY_AFTER, "6"));

        assertReason(catchThrowable(this::post), AiUnavailableException.class, Reason.RATE_LIMITED);
        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    void 누적_시간과_다음_대기의_합이_전체_상한_45초_이상이면_재시도하지_않는다() {
        // 첫 시도가 44.5초 걸리고 실패 -> 다음 대기(최소 0.8초)를 더하면 45초 이상
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(request -> {
            clockNanos.addAndGet(Duration.ofMillis(44_500).toNanos());
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "server_error", null).createResponse(request);
        });

        assertReason(catchThrowable(this::post), AiCallFailedException.class, Reason.SERVER_ERROR);
        assertThat(sleeps).isEmpty();
        server.verify();
    }

    @Test
    void 누적_시간이_상한보다_충분히_짧으면_재시도한다() {
        server.expect(requestTo(URL)).andRespond(request -> {
            clockNanos.addAndGet(Duration.ofSeconds(10).toNanos());
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "server_error", null).createResponse(request);
        });
        server.expect(requestTo(URL)).andRespond(ok());

        assertThat(post().attempts()).isEqualTo(2);
        server.verify();
    }

    @Test
    void 두번째_실패_후_누적_시간이_상한에_닿으면_남은_재시도를_하지_않는다() {
        // 시도마다 22초 -> 1회차 후 22+1 < 45 재시도, 2회차 후 44+2 >= 45 중단
        server.expect(ExpectedCount.times(2), requestTo(URL)).andRespond(request -> {
            clockNanos.addAndGet(Duration.ofSeconds(22).toNanos());
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "server_error", null).createResponse(request);
        });

        assertReason(catchThrowable(this::post), AiCallFailedException.class, Reason.SERVER_ERROR);
        assertThat(sleeps).hasSize(1);
        server.verify();
    }

    @Test
    void 응답_역직렬화에_실패하면_재시도하지_않고_502() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertReason(catchThrowable(this::post), AiCallFailedException.class, Reason.INVALID_RESPONSE);
        server.verify();
    }

    @Test
    void 예외_메시지는_고정_문구이고_로그와_예외에_본문_error_message_키가_없다(CapturedOutput output) {
        server.expect(ExpectedCount.times(3), requestTo(URL))
                .andRespond(error(HttpStatus.INTERNAL_SERVER_ERROR, "server_error", null));
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(error(HttpStatus.BAD_REQUEST, "invalid_request_error", "unsupported_value"));

        Throwable serverError = catchThrowable(this::post);
        Throwable badRequest = catchThrowable(this::post);

        for (Throwable thrown : List.of(serverError, badRequest)) {
            assertThat(thrown).hasMessage(AiCallFailedException.MESSAGE).hasNoCause();
            assertThat(thrown.toString()).doesNotContain(SECRET_MESSAGE, API_KEY);
        }
        assertThat(output.getAll()).contains("errorCode=unsupported_value", "status=400")
                .doesNotContain(SECRET_MESSAGE, API_KEY, "\"error\"");
    }

    @Test
    void 식별자_형태가_아닌_error_code는_로그에_그대로_남기지_않는다(CapturedOutput output) {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(error(HttpStatus.BAD_REQUEST, "invalid_request_error", "has space 정책"));

        catchThrowable(this::post);

        assertThat(output.getAll()).contains("errorCode=unrecognized").doesNotContain("has space 정책");
    }

    @Test
    void 대기_중_인터럽트되면_502() {
        ReflectionTestUtils.setField(executor, "sleeper", (OpenAiHttpExecutor.Sleeper) duration -> {
            throw new InterruptedException();
        });
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(error(HttpStatus.INTERNAL_SERVER_ERROR, "server_error", null));

        assertThatThrownBy(this::post).isInstanceOf(AiCallFailedException.class);
        assertThat(Thread.interrupted()).isTrue(); // 인터럽트 플래그 복원 확인 후 해제
    }
}
