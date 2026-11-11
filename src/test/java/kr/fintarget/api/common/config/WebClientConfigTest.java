package kr.fintarget.api.common.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WebClientConfigTest {

    // 온통청년 100건 페이지 실측 최대(약 330KB)보다 큰 응답
    private static final byte[] LARGE_BODY =
            ("{\"data\":\"" + "가".repeat(200_000) + "\"}").getBytes(StandardCharsets.UTF_8);

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, LARGE_BODY.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(LARGE_BODY);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private WebClientConfig config() {
        WebClientConfig config = new WebClientConfig();
        ReflectionTestUtils.setField(config, "youthCenterBaseUrl", baseUrl);
        ReflectionTestUtils.setField(config, "kinfaBaseUrl", baseUrl);
        return config;
    }

    private String fetch(WebClient webClient) {
        return webClient.get().retrieve().bodyToMono(String.class).block();
    }

    @Test
    void 온통청년_클라이언트는_기본_한도_256KB를_넘는_응답도_받는다() {
        assertThat(LARGE_BODY.length).isGreaterThan(256 * 1024);
        assertThat(fetch(config().youthCenterWebClient()).getBytes(StandardCharsets.UTF_8)).hasSize(LARGE_BODY.length);
    }

    @Test
    void KINFA_클라이언트도_기본_한도_256KB를_넘는_응답을_받는다() {
        assertThat(fetch(config().kinfaWebClient()).getBytes(StandardCharsets.UTF_8)).hasSize(LARGE_BODY.length);
    }
}
