package kr.fintarget.api.domain.expense.service;

import kr.fintarget.api.domain.expense.dto.BankTransactionRequest;
import kr.fintarget.api.domain.expense.dto.BankTransactionResult;
import kr.fintarget.api.domain.expense.dto.ExpenseCategoryStat;
import kr.fintarget.api.domain.expense.dto.ExpenseStatsResponse;
import kr.fintarget.api.domain.expense.dto.ExpenseSyncRequest;
import kr.fintarget.api.domain.expense.entity.Expense;
import kr.fintarget.api.domain.expense.event.ExpenseCreatedEvent;
import kr.fintarget.api.domain.expense.dto.ExpenseResponse;
import kr.fintarget.api.domain.expense.repository.ExpenseRepository;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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

        verify(expenseRepository, never()).insertIfNotExists(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 같은_요청_목록_안에_중복이_있으면_한_번만_저장한다() {
        when(expenseRepository.findByUserIdAndSpentAtBetween(USER_ID, SPENT_AT, SPENT_AT))
                .thenReturn(List.of());

        ExpenseSyncRequest request = new ExpenseSyncRequest(10000L, "FOOD", "점심", SPENT_AT);
        expenseService.syncExpenses(USER_ID, List.of(request, request));

        verify(expenseRepository, times(1)).insertIfNotExists(
                any(), eq(USER_ID), eq("FOOD"), eq(10000L), eq("점심"), eq(SPENT_AT), any());
    }

    @Test
    void 새로운_지출은_정상적으로_저장된다() {
        when(expenseRepository.findByUserIdAndSpentAtBetween(USER_ID, SPENT_AT, SPENT_AT))
                .thenReturn(List.of());

        ExpenseSyncRequest request = new ExpenseSyncRequest(10000L, "FOOD", "점심", SPENT_AT);
        expenseService.syncExpenses(USER_ID, List.of(request));

        verify(expenseRepository).insertIfNotExists(
                any(), eq(USER_ID), eq("FOOD"), eq(10000L), eq("점심"), eq(SPENT_AT), any(LocalDateTime.class));
    }

    @Test
    void 금액이나_설명이_다르면_다른_지출로_저장된다() {
        Expense existing = new Expense(USER_ID, 10000L, "FOOD", "점심", SPENT_AT);
        when(expenseRepository.findByUserIdAndSpentAtBetween(USER_ID, SPENT_AT, SPENT_AT))
                .thenReturn(List.of(existing));

        ExpenseSyncRequest differentAmount = new ExpenseSyncRequest(20000L, "FOOD", "점심", SPENT_AT);
        expenseService.syncExpenses(USER_ID, List.of(differentAmount));

        verify(expenseRepository).insertIfNotExists(
                any(), eq(USER_ID), eq("FOOD"), eq(20000L), eq("점심"), eq(SPENT_AT), any());
    }

    @Test
    void 빈_요청_목록이면_아무것도_하지_않는다() {
        expenseService.syncExpenses(USER_ID, List.of());

        verifyNoInteractions(expenseRepository);
    }

    @Test
    void 설명이_null인_요청도_기존_null_설명_지출과_중복이면_스킵된다() {
        Expense existing = new Expense(USER_ID, 10000L, "FOOD", null, SPENT_AT);
        when(expenseRepository.findByUserIdAndSpentAtBetween(USER_ID, SPENT_AT, SPENT_AT))
                .thenReturn(List.of(existing));

        ExpenseSyncRequest duplicate = new ExpenseSyncRequest(10000L, "FOOD", null, SPENT_AT);
        expenseService.syncExpenses(USER_ID, List.of(duplicate));

        verify(expenseRepository, never()).insertIfNotExists(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 통계는_카테고리별_합계와_총액을_계산한다() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        when(expenseRepository.sumByCategory(USER_ID, start, end)).thenReturn(List.of(
                new ExpenseCategoryStat("FOOD", 15000L),
                new ExpenseCategoryStat("TRANSPORT", 30000L)
        ));

        ExpenseStatsResponse response = expenseService.getExpenseStats(USER_ID, start, end);

        assertThat(response.totalAmount()).isEqualTo(45000L);
        assertThat(response.byCategory()).containsExactlyInAnyOrder(
                new ExpenseCategoryStat("FOOD", 15000L),
                new ExpenseCategoryStat("TRANSPORT", 30000L)
        );
    }

    @Test
    void 통계는_지출이_없으면_총액_0과_빈_목록을_반환한다() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        when(expenseRepository.sumByCategory(USER_ID, start, end)).thenReturn(List.of());

        ExpenseStatsResponse response = expenseService.getExpenseStats(USER_ID, start, end);

        assertThat(response.totalAmount()).isEqualTo(0L);
        assertThat(response.byCategory()).isEmpty();
    }

    @Test
    void 통계는_시작일이_종료일보다_늦으면_예외가_발생한다() {
        LocalDate start = LocalDate.of(2026, 7, 31);
        LocalDate end = LocalDate.of(2026, 7, 1);

        assertThatThrownBy(() -> expenseService.getExpenseStats(USER_ID, start, end))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(expenseRepository);
    }

    @Test
    void 기간_조회도_시작일이_종료일보다_늦으면_예외가_발생한다() {
        LocalDate start = LocalDate.of(2026, 7, 31);
        LocalDate end = LocalDate.of(2026, 7, 1);

        assertThatThrownBy(() -> expenseService.getExpensesByPeriod(USER_ID, start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("시작일이 종료일보다 늦을 수 없습니다.");

        verifyNoInteractions(expenseRepository);
    }

    // ===== 은행 거래 웹훅 수신 (#158) =====

    private static BankTransactionRequest bankTransaction(UUID userId, long amount, String category, String description, LocalDate spentAt) {
        return new BankTransactionRequest(UUID.randomUUID(), userId, amount, category, description, spentAt);
    }

    private void givenUsersExist(UUID... userIds) {
        when(userRepository.findAllById(anyIterable())).thenReturn(
                Arrays.stream(userIds).map(id -> User.builder().id(id.toString()).build()).toList());
    }

    private void givenInsertSucceeds() {
        when(expenseRepository.insertIfNotExists(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    }

    @Test
    void 은행_거래_새_지출은_CREATED로_저장된다() {
        givenUsersExist(USER_ID);
        givenInsertSucceeds();
        BankTransactionRequest request = bankTransaction(USER_ID, 12000L, "식비", "김밥천국", SPENT_AT);

        List<BankTransactionResult> results = expenseService.receiveBankTransactions(List.of(request));

        assertThat(results).containsExactly(new BankTransactionResult(request.transactionId(), BankTransactionResult.Result.CREATED));
        verify(expenseRepository).insertIfNotExists(
                any(), eq(USER_ID), eq("식비"), eq(12000L), eq("김밥천국"), eq(SPENT_AT), any(LocalDateTime.class));
    }

    @Test
    void 은행_거래가_이미_저장된_지출과_같으면_DUPLICATE이고_저장하지_않는다() {
        givenUsersExist(USER_ID);
        when(expenseRepository.findByUserIdAndSpentAtBetween(USER_ID, SPENT_AT, SPENT_AT))
                .thenReturn(List.of(new Expense(USER_ID, 12000L, "식비", "김밥천국", SPENT_AT)));
        BankTransactionRequest request = bankTransaction(USER_ID, 12000L, "식비", "김밥천국", SPENT_AT);

        List<BankTransactionResult> results = expenseService.receiveBankTransactions(List.of(request));

        assertThat(results).extracting(BankTransactionResult::result).containsExactly(BankTransactionResult.Result.DUPLICATE);
        verify(expenseRepository, never()).insertIfNotExists(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 같은_요청_안에_같은_조합이_두_번_있으면_첫_번째만_CREATED이고_결과는_요청_순서를_따른다() {
        givenUsersExist(USER_ID);
        givenInsertSucceeds();
        BankTransactionRequest first = bankTransaction(USER_ID, 4500L, "카페", "스타벅스", SPENT_AT);
        BankTransactionRequest second = bankTransaction(USER_ID, 4500L, "카페", "스타벅스", SPENT_AT);
        BankTransactionRequest other = bankTransaction(USER_ID, 1400L, "교통", "서울교통공사", SPENT_AT);

        List<BankTransactionResult> results = expenseService.receiveBankTransactions(List.of(first, second, other));

        assertThat(results).containsExactly(
                new BankTransactionResult(first.transactionId(), BankTransactionResult.Result.CREATED),
                new BankTransactionResult(second.transactionId(), BankTransactionResult.Result.DUPLICATE),
                new BankTransactionResult(other.transactionId(), BankTransactionResult.Result.CREATED));
        verify(expenseRepository, times(2)).insertIfNotExists(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 조회_이후_다른_요청이_먼저_저장해_삽입이_0건이면_DUPLICATE로_돌려준다() {
        givenUsersExist(USER_ID);
        when(expenseRepository.insertIfNotExists(any(), any(), any(), any(), any(), any(), any())).thenReturn(0);

        List<BankTransactionResult> results = expenseService.receiveBankTransactions(
                List.of(bankTransaction(USER_ID, 12000L, "식비", "김밥천국", SPENT_AT)));

        assertThat(results).extracting(BankTransactionResult::result).containsExactly(BankTransactionResult.Result.DUPLICATE);
    }

    @Test
    void 같은_조합이어도_사용자가_다르면_각각_저장된다() {
        UUID otherUser = UUID.randomUUID();
        givenUsersExist(USER_ID, otherUser);
        givenInsertSucceeds();

        List<BankTransactionResult> results = expenseService.receiveBankTransactions(List.of(
                bankTransaction(USER_ID, 12000L, "식비", "김밥천국", SPENT_AT),
                bankTransaction(otherUser, 12000L, "식비", "김밥천국", SPENT_AT)));

        assertThat(results).extracting(BankTransactionResult::result)
                .containsExactly(BankTransactionResult.Result.CREATED, BankTransactionResult.Result.CREATED);
        verify(expenseRepository).findByUserIdAndSpentAtBetween(USER_ID, SPENT_AT, SPENT_AT);
        verify(expenseRepository).findByUserIdAndSpentAtBetween(otherUser, SPENT_AT, SPENT_AT);
    }

    @Test
    void 기존_지출은_그_사용자가_보낸_거래의_날짜_범위로만_조회한다() {
        givenUsersExist(USER_ID);
        givenInsertSucceeds();
        LocalDate later = SPENT_AT.plusDays(10);

        expenseService.receiveBankTransactions(List.of(
                bankTransaction(USER_ID, 1000L, "교통", null, later),
                bankTransaction(USER_ID, 2000L, "교통", null, SPENT_AT)));

        verify(expenseRepository).findByUserIdAndSpentAtBetween(USER_ID, SPENT_AT, later);
    }

    @Test
    void 존재하지_않는_사용자가_하나라도_있으면_아무것도_저장하지_않고_거부한다() {
        UUID unknownUser = UUID.randomUUID();
        givenUsersExist(USER_ID);

        assertThatThrownBy(() -> expenseService.receiveBankTransactions(List.of(
                bankTransaction(USER_ID, 12000L, "식비", "김밥천국", SPENT_AT),
                bankTransaction(unknownUser, 5000L, "카페", "메가커피", SPENT_AT))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 사용자입니다: " + unknownUser);

        verify(expenseRepository, never()).insertIfNotExists(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 사용자_존재_여부는_중복_없이_한_번에_조회한다() {
        givenUsersExist(USER_ID);
        givenInsertSucceeds();

        expenseService.receiveBankTransactions(List.of(
                bankTransaction(USER_ID, 1000L, "교통", null, SPENT_AT),
                bankTransaction(USER_ID, 2000L, "교통", null, SPENT_AT)));

        verify(userRepository).findAllById(Set.of(USER_ID.toString()));
    }

    @Test
    void 은행_거래가_빈_목록이면_아무것도_조회하지_않고_빈_결과를_반환한다() {
        assertThat(expenseService.receiveBankTransactions(List.of())).isEmpty();

        verifyNoInteractions(userRepository, expenseRepository);
    }

    @Test
    void 은행_거래로_새로_저장된_지출은_사용자별로_묶어_이벤트로_발행한다() {
        UUID otherUser = UUID.randomUUID();
        givenUsersExist(USER_ID, otherUser);
        givenInsertSucceeds();

        expenseService.receiveBankTransactions(List.of(
                bankTransaction(USER_ID, 12000L, "식비", "김밥천국", SPENT_AT),
                bankTransaction(otherUser, 5000L, "교통", null, SPENT_AT),
                bankTransaction(USER_ID, 4500L, "식비", "스타벅스", SPENT_AT)));

        ArgumentCaptor<ExpenseCreatedEvent> captor = ArgumentCaptor.forClass(ExpenseCreatedEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        List<ExpenseCreatedEvent> events = captor.getAllValues();
        assertThat(events).extracting(ExpenseCreatedEvent::userId).containsExactly(USER_ID, otherUser);
        assertThat(events.get(0).expenses()).extracting(ExpenseResponse::amount, ExpenseResponse::description)
                .containsExactly(tuple(12000L, "김밥천국"), tuple(4500L, "스타벅스"));
        assertThat(events.get(1).expenses()).singleElement()
                .satisfies(expense -> assertThat(expense.description()).isEmpty());
    }

    @Test
    void 이벤트의_지출_ID는_실제로_저장한_ID와_같다() {
        givenUsersExist(USER_ID);
        givenInsertSucceeds();

        expenseService.receiveBankTransactions(List.of(bankTransaction(USER_ID, 12000L, "식비", "김밥천국", SPENT_AT)));

        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<LocalDateTime> createdAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(expenseRepository).insertIfNotExists(idCaptor.capture(), any(), any(), any(), any(), any(), createdAtCaptor.capture());
        ArgumentCaptor<ExpenseCreatedEvent> eventCaptor = ArgumentCaptor.forClass(ExpenseCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ExpenseResponse expense = eventCaptor.getValue().expenses().getFirst();
        assertThat(expense.expenseId()).isEqualTo(idCaptor.getValue());
        assertThat(expense.createdAt()).isEqualTo(createdAtCaptor.getValue());
        assertThat(expense.category()).isEqualTo("식비");
        assertThat(expense.spentAt()).isEqualTo(SPENT_AT);
    }

    @Test
    void 은행_거래가_모두_DUPLICATE면_이벤트를_발행하지_않는다() {
        givenUsersExist(USER_ID);
        when(expenseRepository.insertIfNotExists(any(), any(), any(), any(), any(), any(), any())).thenReturn(0);

        expenseService.receiveBankTransactions(List.of(bankTransaction(USER_ID, 12000L, "식비", "김밥천국", SPENT_AT)));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void 존재하지_않는_사용자로_거부되면_이벤트를_발행하지_않는다() {
        givenUsersExist();

        assertThatThrownBy(() -> expenseService.receiveBankTransactions(
                List.of(bankTransaction(USER_ID, 12000L, "식비", "김밥천국", SPENT_AT))))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void 앱_동기화로_저장한_지출은_이벤트를_발행하지_않는다() {
        when(expenseRepository.findByUserIdAndSpentAtBetween(USER_ID, SPENT_AT, SPENT_AT)).thenReturn(List.of());

        expenseService.syncExpenses(USER_ID, List.of(new ExpenseSyncRequest(10000L, "식비", "점심", SPENT_AT)));

        verifyNoInteractions(eventPublisher);
    }
}
