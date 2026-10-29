package kr.fintarget.api.domain.ai.client;

import com.sun.net.httpserver.HttpServer;
import kr.fintarget.api.common.config.OpenAiConfig;
import kr.fintarget.api.common.config.OpenAiProperties;
import kr.fintarget.api.domain.ai.client.AiClientException.Reason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 실제 HTTP 연결로 타임아웃과 연결 실패를 확인한다.
 * MockRestServiceServer는 요청 팩토리를 바꿔치기해 OpenAiConfig의 타임아웃이 적용되지 않으므로 JDK HttpServer를 쓴다.
 */
class OpenAiTimeoutTest {

    private HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();
    private final List<Duration> sleeps = new ArrayList<>();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startServer(Duration responseDelay, int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            requestCount.incrementAndGet();
            try {
                Thread.sleep(responseDelay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } catch (IOException ignored) {
                // 클라이언트가 타임아웃으로 먼저 끊은 경우
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private OpenAiHttpExecutor executor(String baseUrl, Duration readTimeout) {
        OpenAiProperties properties = new OpenAiProperties("sk-test", baseUrl, null, null, null,
                Duration.ofMillis(500), readTimeout, null, null, null);
        RestClient restClient = new OpenAiConfig().openAiRestClient(RestClient.builder(), properties);
        OpenAiHttpExecutor executor = new OpenAiHttpExecutor(restClient, properties);
        ReflectionTestUtils.setField(executor, "sleeper", (OpenAiHttpExecutor.Sleeper) sleeps::add);
        return executor;
    }

    @Test
    void 읽기_타임아웃은_재시도하지_않고_502() throws IOException {
        String baseUrl = startServer(Duration.ofSeconds(3), 200, "{\"ok\":true}");
        OpenAiHttpExecutor executor = executor(baseUrl, Duration.ofMillis(300));

        long startedAt = System.nanoTime();
        Throwable thrown = catchThrowable(() -> executor.post("responses", "/responses", Map.of(), Map.class));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(thrown).isInstanceOf(AiCallFailedException.class);
        assertThat(((AiClientException) thrown).getReason()).isEqualTo(Reason.TIMEOUT);
        assertThat(requestCount.get()).isEqualTo(1);
        assertThat(sleeps).isEmpty();
        assertThat(elapsed).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void 읽기_타임아웃_안에_응답하면_성공한다() throws IOException {
        String baseUrl = startServer(Duration.ZERO, 200, "{\"ok\":true}");
        OpenAiHttpExecutor executor = executor(baseUrl, Duration.ofSeconds(5));

        OpenAiCallResult<Map> result = executor.post("responses", "/responses", Map.of(), Map.class);

        assertThat(result.body()).containsEntry("ok", true);
    }

    @Test
    void 실제_HTTP의_500_오류도_재시도_후_502() throws IOException {
        String baseUrl = startServer(Duration.ZERO, 500, "{\"error\":{\"message\":\"x\",\"type\":\"server_error\"}}");
        OpenAiHttpExecutor executor = executor(baseUrl, Duration.ofSeconds(5));

        Throwable thrown = catchThrowable(() -> executor.post("responses", "/responses", Map.of(), Map.class));

        assertThat(((AiClientException) thrown).getReason()).isEqualTo(Reason.SERVER_ERROR);
        assertThat(requestCount.get()).isEqualTo(3);
    }

    @Test
    void 연결_실패는_재시도_후_502() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        OpenAiHttpExecutor executor = executor("http://127.0.0.1:" + closedPort + "/v1", Duration.ofSeconds(5));

        Throwable thrown = catchThrowable(() -> executor.post("responses", "/responses", Map.of(), Map.class));

        assertThat(thrown).isInstanceOf(AiCallFailedException.class);
        assertThat(((AiClientException) thrown).getReason()).isEqualTo(Reason.CONNECTION_FAILED);
        assertThat(sleeps).hasSize(2);
    }
}
