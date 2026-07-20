package kr.fintarget.api.domain.expense.service;

import kr.fintarget.api.domain.expense.dto.ExpenseSyncRequest;
import kr.fintarget.api.domain.expense.entity.Expense;
import kr.fintarget.api.domain.expense.repository.ExpenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private ExpenseService expenseService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate SPENT_AT = LocalDate.of(2026, 7, 1);

    @Test
    void 이미_저장된_것과_동일한_요청은_다시_저장하지_않는다() {
        Expense existing = new Expense(USER_ID, 10000L, "FOOD", "점심", SPENT_AT);
        when(expenseRepository.findByUserIdAndSpentAtBetween(USER_ID, SPENT_AT, SPENT_AT))
                .thenReturn(List.of(existing));

        ExpenseSyncRequest duplicate = new ExpenseSyncRequest(10000L, "FOOD", "점심", SPENT_AT);
        expenseService.syncExpenses(USER_ID, List.of(duplicate));

        ArgumentCaptor<List<Expense>> captor = ArgumentCaptor.forClass(List.class);
        verify(expenseRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void 같은_요청_목록_안에_중복이_있으면_한_번만_저장한다() {
        when(expenseRepository.findByUserIdAndSpentAtBetween(USER_ID, SPENT_AT, SPENT_AT))
                .thenReturn(List.of());

        ExpenseSyncRequest request = new ExpenseSyncRequest(10000L, "FOOD", "점심", SPENT_AT);
        expenseService.syncExpenses(USER_ID, List.of(request, request));

        ArgumentCaptor<List<Expense>> captor = ArgumentCaptor.forClass(List.class);
        verify(expenseRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    void 새로운_지출은_정상적으로_저장된다() {
        when(expenseRepository.findByUserIdAndSpentAtBetween(USER_ID, SPENT_AT, SPENT_AT))
                .thenReturn(List.of());

        ExpenseSyncRequest request = new ExpenseSyncRequest(10000L, "FOOD", "점심", SPENT_AT);
        expenseService.syncExpenses(USER_ID, List.of(request));

        ArgumentCaptor<List<Expense>> captor = ArgumentCaptor.forClass(List.class);
        verify(expenseRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getAmount()).isEqualTo(10000L);
        assertThat(captor.getValue().get(0).getCategory()).isEqualTo("FOOD");
    }

    @Test
    void 금액이나_설명이_다르면_다른_지출로_저장된다() {
        Expense existing = new Expense(USER_ID, 10000L, "FOOD", "점심", SPENT_AT);
        when(expenseRepository.findByUserIdAndSpentAtBetween(USER_ID, SPENT_AT, SPENT_AT))
                .thenReturn(List.of(existing));

        ExpenseSyncRequest differentAmount = new ExpenseSyncRequest(20000L, "FOOD", "점심", SPENT_AT);
        expenseService.syncExpenses(USER_ID, List.of(differentAmount));

        ArgumentCaptor<List<Expense>> captor = ArgumentCaptor.forClass(List.class);
        verify(expenseRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }
}
