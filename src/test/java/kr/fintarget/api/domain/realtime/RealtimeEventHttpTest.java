package kr.fintarget.api.domain.realtime;

import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import kr.fintarget.api.security.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Tomcat에서 SSE 연결 → 웹훅으로 지출 저장 → 같은 연결로 expense-created 수신까지 확인한다.
 * MockMvc는 비동기 재디스패치와 서블릿 필터 순서를 실제와 다르게 처리하므로 실제 HTTP로 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"BANK_WEBHOOK_KEY=test-webhook-key", "realtime.sse-timeout=2s"})
class RealtimeEventHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String userId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.builder()
                .provider(SocialProvider.KAKAO)
                .providerId("realtime-http-" + UUID.randomUUID())
                .build()).getId();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    @Test
    void 연결한_앱은_웹훅으로_저장된_지출을_실시간으로_받는다() throws Exception {
        HttpRequest streamRequest = HttpRequest.newBuilder(uri("/api/events/stream"))
                .header("Authorization", "Bearer " + jwtUtil.generateToken(userId, "KAKAO"))
                .header("Accept", "text/event-stream")
                .GET().build();
        HttpResponse<InputStream> stream = client.send(streamRequest, HttpResponse.BodyHandlers.ofInputStream());
        assertThat(stream.statusCode()).isEqualTo(200);
        assertThat(stream.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type).startsWith("text/event-stream"));

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream.body(), StandardCharsets.UTF_8));
        assertThat(reader.readLine()).isEqualTo("event:connected");

        Future<String> expenseEvent = executor.submit(() -> {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals("event:expense-created")) {
                    return reader.readLine();
                }
            }
            return null;
        });

        String body = """
                [{"transactionId": "%s", "userId": "%s", "amount": 15000, "category": "식비", "description": "스타벅스", "spentAt": "2026-11-04"}]
                """.formatted(UUID.randomUUID(), userId);
        HttpResponse<String> webhook = client.send(HttpRequest.newBuilder(uri("/api/webhooks/bank-transactions"))
                        .header("Content-Type", "application/json")
                        .header("X-Bank-Webhook-Key", "test-webhook-key")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(webhook.statusCode()).isEqualTo(200);

        assertThat(expenseEvent.get(5, TimeUnit.SECONDS))
                .startsWith("data:")
                .contains("\"amount\":15000")
                .contains("\"description\":\"스타벅스\"");
    }

    @Test
    void 시간이_지나면_연결이_정상적으로_닫힌다() throws Exception {
        HttpResponse<InputStream> stream = client.send(HttpRequest.newBuilder(uri("/api/events/stream"))
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(userId, "KAKAO"))
                        .header("Accept", "text/event-stream").GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream.body(), StandardCharsets.UTF_8));

        Future<String> rest = executor.submit(() -> {
            StringBuilder lines = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.append(line).append('\n');
            }
            return lines.toString();
        });

        // 연결 종료(스트림 끝)까지 읽히고, 오류 응답(JSON)이 끼어들지 않는다
        assertThat(rest.get(10, TimeUnit.SECONDS)).startsWith("event:connected").doesNotContain("\"status\"");
    }

    @Test
    void 토큰_없이_연결하면_401() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/api/events/stream"))
                .header("Accept", "text/event-stream").GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }
}
