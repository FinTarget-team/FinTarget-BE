package kr.fintarget.mockbank.webhook;

import kr.fintarget.mockbank.webhook.dto.WebhookTransactionPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FinTargetWebhookClientTest {

    private static final String BASE_URL = "http://fintarget.test";
    private static final UUID TX_1 = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final UUID TX_2 = UUID.fromString("1b4e28ba-2fa1-11d2-883f-0016d3cca427");
    private static final UUID USER_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

    private MockRestServiceServer server;
    private FinTargetWebhookClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FinTargetWebhookClient(builder.build(), "test-key");
    }

    private static List<WebhookTransactionPayload> payloads() {
        return List.of(
                new WebhookTransactionPayload(TX_1, USER_ID, 12_000L, "식비", "김밥천국", LocalDate.of(2026, 11, 2)),
                new WebhookTransactionPayload(TX_2, USER_ID, 4_500L, "생활", null, LocalDate.of(2026, 11, 1)));
    }

    @Test
    void 메인_서버_형식으로_키_헤더와_거래_목록을_보내고_거래별_결과를_돌려준다() {
        server.expect(requestTo(BASE_URL + "/api/webhooks/bank-transactions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Bank-Webhook-Key", "test-key"))
                .andExpect(jsonPath("$[0].transactionId").value(TX_1.toString()))
                .andExpect(jsonPath("$[0].userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$[0].amount").value(12000))
                .andExpect(jsonPath("$[0].category").value("식비"))
                .andExpect(jsonPath("$[0].description").value("김밥천국"))
                .andExpect(jsonPath("$[0].spentAt").value("2026-11-02"))
                .andExpect(jsonPath("$[1].spentAt").value("2026-11-01"))
                .andRespond(withSuccess("""
                        {"status": 200, "message": "OK", "data": [
                          {"transactionId": "%s", "result": "CREATED"},
                          {"transactionId": "%s", "result": "DUPLICATE"}
                        ]}
                        """.formatted(TX_1, TX_2), MediaType.APPLICATION_JSON));

        Map<UUID, String> results = client.send(payloads());

        assertThat(results).containsExactlyInAnyOrderEntriesOf(Map.of(TX_1, "CREATED", TX_2, "DUPLICATE"));
        server.verify();
    }

    @Test
    void 메인_서버가_거부하면_응답_메시지를_담아_실패한다() {
        server.expect(requestTo(BASE_URL + "/api/webhooks/bank-transactions"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status": 403, "message": "웹훅 키가 올바르지 않습니다.", "data": null}
                                """));

        assertThatThrownBy(() -> client.send(payloads()))
                .isInstanceOf(WebhookSendException.class)
                .hasMessage("핀타겟 서버가 거부했습니다 (HTTP 403): 웹훅 키가 올바르지 않습니다.");
    }

    @Test
    void 메인_서버_검증_실패_메시지도_그대로_전달한다() {
        server.expect(requestTo(BASE_URL + "/api/webhooks/bank-transactions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status": 400, "message": "존재하지 않는 사용자입니다: %s", "data": null}
                                """.formatted(USER_ID)));

        assertThatThrownBy(() -> client.send(payloads()))
                .isInstanceOf(WebhookSendException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("존재하지 않는 사용자입니다");
    }

    @Test
    void 오류_응답_본문이_JSON이_아니어도_상태_코드로_실패한다() {
        server.expect(requestTo(BASE_URL + "/api/webhooks/bank-transactions"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML).body("<html>502</html>"));

        assertThatThrownBy(() -> client.send(payloads()))
                .isInstanceOf(WebhookSendException.class)
                .hasMessage("핀타겟 서버가 거부했습니다 (HTTP 502): 응답 내용 없음");
    }

    @Test
    void 연결에_실패하면_서버_확인_안내와_함께_실패한다() {
        server.expect(requestTo(BASE_URL + "/api/webhooks/bank-transactions"))
                .andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> client.send(payloads()))
                .isInstanceOf(WebhookSendException.class)
                .hasMessageContaining("핀타겟 서버에 연결하지 못했습니다");
    }

    @Test
    void 성공_응답에_처리_결과가_없으면_실패한다() {
        server.expect(requestTo(BASE_URL + "/api/webhooks/bank-transactions"))
                .andRespond(withSuccess("""
                        {"status": 200, "message": "OK", "data": null}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.send(payloads()))
                .isInstanceOf(WebhookSendException.class)
                .hasMessage("핀타겟 서버 응답에 처리 결과가 없습니다.");
    }
}
