package kr.fintarget.mockbank.webhook;

import kr.fintarget.mockbank.transaction.entity.Transaction;
import kr.fintarget.mockbank.transaction.entity.WebhookStatus;
import kr.fintarget.mockbank.transaction.service.TransactionService;
import kr.fintarget.mockbank.webhook.dto.WebhookTransactionPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 저장된 거래를 핀타겟으로 보내고 결과에 따라 웹훅 상태를 바꾼다.
 * HTTP 호출이 DB 트랜잭션을 붙잡지 않도록, 거래는 먼저 저장(커밋)된 상태에서 보내고 결과만 따로 반영한다.
 * 전송에 실패해도 거래는 남고 FAILED로 표시되며, 재전송하면 메인 서버가 중복을 판정하므로 안전하다.
 */
@Service
@RequiredArgsConstructor
public class WebhookDeliveryService {

    // 메인 서버의 요청당 최대 건수(1000)보다 작게 나눠 보낸다
    static final int CHUNK_SIZE = 500;

    private final FinTargetWebhookClient webhookClient;
    private final TransactionService transactionService;

    public DeliveryResult deliver(List<Transaction> transactions) {
        int sent = 0;
        int duplicate = 0;
        int failed = 0;
        String errorMessage = null;

        for (int from = 0; from < transactions.size(); from += CHUNK_SIZE) {
            List<Transaction> chunk = transactions.subList(from, Math.min(from + CHUNK_SIZE, transactions.size()));
            Map<UUID, WebhookStatus> statuses = new HashMap<>();
            try {
                Map<UUID, String> results = webhookClient.send(chunk.stream().map(WebhookTransactionPayload::from).toList());
                for (Transaction transaction : chunk) {
                    statuses.put(transaction.getId(), toStatus(results.get(transaction.getId())));
                }
            } catch (WebhookSendException e) {
                chunk.forEach(transaction -> statuses.put(transaction.getId(), WebhookStatus.FAILED));
                if (errorMessage == null) {
                    errorMessage = e.getMessage();
                }
            }
            transactionService.updateWebhookStatuses(statuses);

            for (WebhookStatus status : statuses.values()) {
                switch (status) {
                    case SENT -> sent++;
                    case DUPLICATE -> duplicate++;
                    default -> failed++;
                }
            }
        }
        if (failed > 0 && errorMessage == null) {
            errorMessage = "핀타겟 서버 응답에 일부 거래의 처리 결과가 없습니다.";
        }
        return new DeliveryResult(sent, duplicate, failed, errorMessage);
    }

    public DeliveryResult resendUnsent() {
        return deliver(transactionService.getUnsentTransactions());
    }

    private static WebhookStatus toStatus(String result) {
        if ("CREATED".equals(result)) {
            return WebhookStatus.SENT;
        }
        if ("DUPLICATE".equals(result)) {
            return WebhookStatus.DUPLICATE;
        }
        // 응답에 이 거래 결과가 없거나 모르는 값이면 다시 보낼 수 있게 실패로 둔다
        return WebhookStatus.FAILED;
    }
}
