package kr.fintarget.mockbank.transaction.service;

import kr.fintarget.mockbank.transaction.entity.Transaction;
import kr.fintarget.mockbank.transaction.entity.WebhookStatus;
import kr.fintarget.mockbank.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TransactionServiceTest {

    @Autowired
    private TransactionRepository transactionRepository;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(transactionRepository);
    }

    @Test
    void 직접_입력한_거래는_PENDING_상태로_저장된다() {
        UUID userId = UUID.randomUUID();

        Transaction saved = transactionService.create(userId, 12_000, "식비", " 김밥천국 ", LocalDate.of(2026, 11, 2));
        transactionRepository.flush();

        Transaction found = transactionRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getFinTargetUserId()).isEqualTo(userId);
        assertThat(found.getAmount()).isEqualTo(12_000);
        assertThat(found.getCategory()).isEqualTo("식비");
        assertThat(found.getDescription()).isEqualTo("김밥천국");
        assertThat(found.getWebhookStatus()).isEqualTo(WebhookStatus.PENDING);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void 설명이_비어_있으면_null로_저장된다() {
        Transaction saved = transactionService.create(UUID.randomUUID(), 5_000, "카페", "   ", LocalDate.of(2026, 11, 2));

        assertThat(transactionRepository.findById(saved.getId()).orElseThrow().getDescription()).isNull();
    }

    @Test
    void 직접_입력은_같은_조합이어도_실제_은행처럼_모두_저장된다() {
        UUID userId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 2);

        transactionService.create(userId, 4_500, "카페", "스타벅스", date);
        transactionService.create(userId, 4_500, "카페", "스타벅스", date);

        assertThat(transactionRepository.count()).isEqualTo(2);
    }
}
