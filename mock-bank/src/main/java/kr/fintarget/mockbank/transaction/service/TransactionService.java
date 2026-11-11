package kr.fintarget.mockbank.transaction.service;

import kr.fintarget.mockbank.transaction.entity.Transaction;
import kr.fintarget.mockbank.transaction.entity.WebhookStatus;
import kr.fintarget.mockbank.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    // 직접 입력한 거래는 실제 은행처럼 같은 조합이어도 저장한다. 메인 서버 중복 여부는 웹훅 전송 시 DUPLICATE로 기록할 예정
    @Transactional
    public Transaction create(UUID finTargetUserId, long amount, String category, String description, LocalDate spentAt) {
        String normalizedDescription = description == null || description.isBlank() ? null : description.trim();
        return transactionRepository.save(new Transaction(finTargetUserId, amount, category, normalizedDescription, spentAt));
    }

    @Transactional(readOnly = true)
    public List<Transaction> getRecentTransactions() {
        return transactionRepository.findTop20ByOrderByCreatedAtDesc();
    }

    // 핀타겟으로 아직 전달되지 않은 거래(미전송·실패)
    @Transactional(readOnly = true)
    public List<Transaction> getUnsentTransactions() {
        return transactionRepository.findByWebhookStatusInOrderByCreatedAtAsc(UNSENT_STATUSES);
    }

    @Transactional(readOnly = true)
    public long countUnsentTransactions() {
        return transactionRepository.countByWebhookStatusIn(UNSENT_STATUSES);
    }

    @Transactional
    public void updateWebhookStatuses(Map<UUID, WebhookStatus> statuses) {
        transactionRepository.findAllById(statuses.keySet())
                .forEach(transaction -> transaction.changeWebhookStatus(statuses.get(transaction.getId())));
    }

    private static final List<WebhookStatus> UNSENT_STATUSES = List.of(WebhookStatus.PENDING, WebhookStatus.FAILED);
}
