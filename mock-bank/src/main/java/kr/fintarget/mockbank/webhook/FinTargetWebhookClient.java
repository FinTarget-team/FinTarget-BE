package kr.fintarget.mockbank.webhook;

import kr.fintarget.mockbank.webhook.dto.WebhookResponse;
import kr.fintarget.mockbank.webhook.dto.WebhookTransactionPayload;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 핀타겟 메인 서버 웹훅(POST /api/webhooks/bank-transactions) 호출.
 */
public class FinTargetWebhookClient {

    static final String PATH = "/api/webhooks/bank-transactions";
    static final String KEY_HEADER = "X-Bank-Webhook-Key";

    private final RestClient restClient;
    private final String webhookKey;

    public FinTargetWebhookClient(RestClient restClient, String webhookKey) {
        this.restClient = restClient;
        this.webhookKey = webhookKey;
    }

    /**
     * @return 거래 ID별 메인 서버 처리 결과 (CREATED / DUPLICATE)
     * @throws WebhookSendException 연결 실패, 타임아웃, 메인 서버의 2xx 이외 응답
     */
    public Map<UUID, String> send(List<WebhookTransactionPayload> payloads) {
        try {
            WebhookResponse response = restClient.post()
                    .uri(PATH)
                    .header(KEY_HEADER, webhookKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payloads)
                    .exchange((request, httpResponse) -> {
                        if (!httpResponse.getStatusCode().is2xxSuccessful()) {
                            throw new WebhookSendException(errorMessage(httpResponse.getStatusCode().value(), readBody(httpResponse)));
                        }
                        return httpResponse.bodyTo(WebhookResponse.class);
                    });
            if (response == null || response.data() == null) {
                throw new WebhookSendException("핀타겟 서버 응답에 처리 결과가 없습니다.");
            }
            return response.data().stream()
                    .collect(Collectors.toMap(WebhookResponse.Item::transactionId, WebhookResponse.Item::result, (first, second) -> first));
        } catch (ResourceAccessException e) {
            throw new WebhookSendException("핀타겟 서버에 연결하지 못했습니다. 서버 주소와 실행 여부를 확인해 주세요.", e);
        } catch (RestClientException e) {
            throw new WebhookSendException("핀타겟 서버 응답을 처리하지 못했습니다.", e);
        }
    }

    private static WebhookResponse readBody(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            return response.bodyTo(WebhookResponse.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String errorMessage(int status, WebhookResponse body) {
        String reason = body != null && body.message() != null ? body.message() : "응답 내용 없음";
        return "핀타겟 서버가 거부했습니다 (HTTP " + status + "): " + reason;
    }
}
