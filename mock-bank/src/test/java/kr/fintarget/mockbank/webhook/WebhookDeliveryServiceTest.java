package kr.fintarget.mockbank.webhook;

import kr.fintarget.mockbank.transaction.entity.Transaction;
import kr.fintarget.mockbank.transaction.entity.WebhookStatus;
import kr.fintarget.mockbank.transaction.repository.TransactionRepository;
import kr.fintarget.mockbank.transaction.service.TransactionService;
import kr.fintarget.mockbank.webhook.dto.WebhookTransactionPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest
class WebhookDeliveryServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 11, 2);

    @Autowired
    private TransactionRepository transactionRepository;

    private FinTargetWebhookClient webhookClient;
    private WebhookDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        webhookClient = mock(FinTargetWebhookClient.class);
        deliveryService = new WebhookDeliveryService(webhookClient, new TransactionService(transactionRepository));
    }

    private Transaction save(long amount) {
        return transactionRepository.save(new Transaction(USER_ID, amount, "식비", "김밥천국", DATE));
    }

    private WebhookStatus statusOf(Transaction transaction) {
        return transactionRepository.findById(transaction.getId()).orElseThrow().getWebhookStatus();
    }

    @Test
    void 메인_서버_결과에_따라_SENT와_DUPLICATE로_바꾼다() {
        Transaction created = save(12_000);
        Transaction duplicate = save(13_000);
        when(webhookClient.send(anyList())).thenReturn(Map.of(created.getId(), "CREATED", duplicate.getId(), "DUPLICATE"));

        DeliveryResult result = deliveryService.deliver(List.of(created, duplicate));

        assertThat(result).isEqualTo(new DeliveryResult(1, 1, 0, null));
        assertThat(statusOf(created)).isEqualTo(WebhookStatus.SENT);
        assertThat(statusOf(duplicate)).isEqualTo(WebhookStatus.DUPLICATE);
    }

    @Test
    void 전송에_실패하면_거래는_남기고_FAILED와_실패_사유를_돌려준다() {
        Transaction transaction = save(12_000);
        when(webhookClient.send(anyList())).thenThrow(new WebhookSendException("핀타겟 서버가 거부했습니다 (HTTP 403): 웹훅 키가 올바르지 않습니다."));

        DeliveryResult result = deliveryService.deliver(List.of(transaction));

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errorMessage()).contains("웹훅 키가 올바르지 않습니다.");
        assertThat(statusOf(transaction)).isEqualTo(WebhookStatus.FAILED);
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void 응답에_결과가_빠진_거래는_다시_보낼_수_있게_FAILED로_둔다() {
        Transaction answered = save(12_000);
        Transaction missing = save(13_000);
        when(webhookClient.send(anyList())).thenReturn(Map.of(answered.getId(), "CREATED"));

        DeliveryResult result = deliveryService.deliver(List.of(answered, missing));

        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errorMessage()).isNotBlank();
        assertThat(statusOf(missing)).isEqualTo(WebhookStatus.FAILED);
    }

    @Test
    void 재전송은_PENDING과_FAILED_거래만_보낸다() {
        Transaction pending = save(1_000);
        Transaction failed = save(2_000);
        Transaction sent = save(3_000);
        Transaction duplicate = save(4_000);
        failed.changeWebhookStatus(WebhookStatus.FAILED);
        sent.changeWebhookStatus(WebhookStatus.SENT);
        duplicate.changeWebhookStatus(WebhookStatus.DUPLICATE);
        transactionRepository.flush();

        Map<UUID, String> response = new HashMap<>();
        response.put(pending.getId(), "CREATED");
        response.put(failed.getId(), "DUPLICATE");
        when(webhookClient.send(anyList())).thenAnswer(invocation -> {
            List<WebhookTransactionPayload> payloads = invocation.getArgument(0);
            assertThat(payloads).extracting(WebhookTransactionPayload::transactionId)
                    .containsExactlyInAnyOrder(pending.getId(), failed.getId());
            return response;
        });

        DeliveryResult result = deliveryService.resendUnsent();

        assertThat(result).isEqualTo(new DeliveryResult(1, 1, 0, null));
        assertThat(statusOf(pending)).isEqualTo(WebhookStatus.SENT);
        assertThat(statusOf(failed)).isEqualTo(WebhookStatus.DUPLICATE);
    }

    @Test
    void 재전송할_거래가_없으면_호출하지_않는다() {
        Transaction sent = save(1_000);
        sent.changeWebhookStatus(WebhookStatus.SENT);
        transactionRepository.flush();

        assertThat(deliveryService.resendUnsent()).isEqualTo(DeliveryResult.EMPTY);
        verify(webhookClient, never()).send(anyList());
    }

    @Test
    void 메인_서버_한도보다_작은_묶음으로_나눠_보낸다() {
        List<Transaction> transactions = IntStream.range(0, WebhookDeliveryService.CHUNK_SIZE + 1)
                .mapToObj(i -> save(1_000L + i))
                .toList();
        when(webhookClient.send(anyList())).thenAnswer(invocation -> {
            List<WebhookTransactionPayload> payloads = invocation.getArgument(0);
            assertThat(payloads.size()).isLessThanOrEqualTo(WebhookDeliveryService.CHUNK_SIZE);
            Map<UUID, String> results = new HashMap<>();
            payloads.forEach(payload -> results.put(payload.transactionId(), "CREATED"));
            return results;
        });

        DeliveryResult result = deliveryService.deliver(transactions);

        verify(webhookClient, times(2)).send(anyList());
        assertThat(result.sent()).isEqualTo(WebhookDeliveryService.CHUNK_SIZE + 1);
    }

    @Test
    void 한_묶음이_실패해도_다른_묶음은_계속_보낸다() {
        List<Transaction> transactions = IntStream.range(0, WebhookDeliveryService.CHUNK_SIZE + 1)
                .mapToObj(i -> save(1_000L + i))
                .toList();
        when(webhookClient.send(anyList()))
                .thenThrow(new WebhookSendException("핀타겟 서버에 연결하지 못했습니다."))
                .thenAnswer(invocation -> {
                    List<WebhookTransactionPayload> payloads = invocation.getArgument(0);
                    Map<UUID, String> results = new HashMap<>();
                    payloads.forEach(payload -> results.put(payload.transactionId(), "CREATED"));
                    return results;
                });

        DeliveryResult result = deliveryService.deliver(transactions);

        assertThat(result.failed()).isEqualTo(WebhookDeliveryService.CHUNK_SIZE);
        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.errorMessage()).isEqualTo("핀타겟 서버에 연결하지 못했습니다.");
    }
}
