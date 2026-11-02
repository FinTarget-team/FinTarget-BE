package kr.fintarget.mockbank.webhook.dto;

import kr.fintarget.mockbank.transaction.entity.Transaction;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 메인 서버 POST /api/webhooks/bank-transactions 요청 항목 (메인 서버 BankTransactionRequest와 같은 형식)
 */
public record WebhookTransactionPayload(
        UUID transactionId,
        UUID userId,
        Long amount,
        String category,
        String description,
        LocalDate spentAt
) {
    public static WebhookTransactionPayload from(Transaction transaction) {
        return new WebhookTransactionPayload(
                transaction.getId(),
                transaction.getFinTargetUserId(),
                transaction.getAmount(),
                transaction.getCategory(),
                transaction.getDescription(),
                transaction.getSpentAt());
    }
}
