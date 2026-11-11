package kr.fintarget.api.domain.realtime;

import kr.fintarget.api.domain.expense.dto.BankTransactionRequest;
import kr.fintarget.api.domain.expense.repository.ExpenseRepository;
import kr.fintarget.api.domain.expense.service.ExpenseService;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 실제 트랜잭션에서 지출 저장 → 커밋 → 실시간 전송 순서가 지켜지는지 확인한다.
 */
@SpringBootTest
class ExpenseRealtimeEventIntegrationTest {

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private SseEmitterRegistry sseEmitterRegistry;

    private UUID userId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .provider(SocialProvider.NAVER)
                .providerId("realtime-test-" + UUID.randomUUID())
                .build());
        userId = UUID.fromString(user.getId());
    }

    @AfterEach
    void tearDown() {
        expenseRepository.deleteAll(expenseRepository.findByUserId(userId));
        userRepository.deleteById(userId.toString());
    }

    private BankTransactionRequest transaction(long amount) {
        return new BankTransactionRequest(UUID.randomUUID(), userId, amount, "식비", "스타벅스", LocalDate.of(2026, 11, 4));
    }

    @Test
    void 커밋되면_그_사용자에게_expense_created를_보낸다() {
        expenseService.receiveBankTransactions(List.of(transaction(15000L)));

        verify(sseEmitterRegistry).sendToUser(eq(userId.toString()), eq(ExpenseRealtimeListener.EXPENSE_CREATED),
                argThat(data -> data instanceof Map<?, ?> map && map.get("expenses") instanceof List<?> list && list.size() == 1));
    }

    @Test
    void 롤백되면_보내지_않는다() {
        transactionTemplate.executeWithoutResult(status -> {
            expenseService.receiveBankTransactions(List.of(transaction(15000L)));
            status.setRollbackOnly();
        });

        verify(sseEmitterRegistry, never()).sendToUser(anyString(), anyString(), any());
        assertThat(expenseRepository.findByUserId(userId)).isEmpty();
    }

    @Test
    void 이미_있는_지출만_다시_오면_보내지_않는다() {
        expenseService.receiveBankTransactions(List.of(transaction(15000L)));
        clearInvocations(sseEmitterRegistry);

        expenseService.receiveBankTransactions(List.of(transaction(15000L)));

        verify(sseEmitterRegistry, never()).sendToUser(anyString(), anyString(), any());
    }

    @Test
    void 실시간_전송이_실패해도_지출은_저장된다() {
        doThrow(new IllegalStateException("전송 실패")).when(sseEmitterRegistry).sendToUser(anyString(), anyString(), any());

        expenseService.receiveBankTransactions(List.of(transaction(15000L)));

        assertThat(expenseRepository.findByUserId(userId)).hasSize(1);
    }
}
