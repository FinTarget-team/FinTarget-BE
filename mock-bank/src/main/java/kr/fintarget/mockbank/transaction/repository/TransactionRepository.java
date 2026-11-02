package kr.fintarget.mockbank.transaction.repository;

import kr.fintarget.mockbank.transaction.entity.Transaction;
import kr.fintarget.mockbank.transaction.entity.WebhookStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findTop20ByOrderByCreatedAtDesc();

    List<Transaction> findByFinTargetUserIdAndSpentAtBetween(UUID finTargetUserId, LocalDate start, LocalDate end);

    List<Transaction> findByFinTargetUserIdAndCategoryAndSpentAtBefore(UUID finTargetUserId, String category, LocalDate before);

    List<Transaction> findByWebhookStatusInOrderByCreatedAtAsc(Collection<WebhookStatus> statuses);

    long countByWebhookStatusIn(Collection<WebhookStatus> statuses);
}
