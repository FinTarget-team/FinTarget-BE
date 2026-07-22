package kr.fintarget.api.domain.expense.service;

import kr.fintarget.api.domain.expense.dto.ExpenseCategoryStat;
import kr.fintarget.api.domain.expense.dto.ExpenseResponse;
import kr.fintarget.api.domain.expense.dto.ExpenseStatsResponse;
import kr.fintarget.api.domain.expense.dto.ExpenseSyncRequest;
import kr.fintarget.api.domain.expense.entity.Expense;
import kr.fintarget.api.domain.expense.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;

    @Transactional
    public void syncExpenses(UUID userId, List<ExpenseSyncRequest> requests) {
        if (requests.isEmpty()) {
            return;
        }

        LocalDate minDate = requests.stream().map(ExpenseSyncRequest::spentAt).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate maxDate = requests.stream().map(ExpenseSyncRequest::spentAt).max(Comparator.naturalOrder()).orElseThrow();

        Set<ExpenseKey> seen = expenseRepository.findByUserIdAndSpentAtBetween(userId, minDate, maxDate)
            .stream()
            .map(ExpenseKey::from)
            .collect(Collectors.toCollection(HashSet::new));

        for (ExpenseSyncRequest request : requests) {
            if (seen.add(ExpenseKey.from(request))) {
                expenseRepository.insertIfNotExists(
                    UUID.randomUUID(),
                    userId,
                    request.category(),
                    request.amount(),
                    request.description(),
                    request.spentAt(),
                    LocalDateTime.now()
                );
            }
        }
    }

    private record ExpenseKey(String category, Long amount, LocalDate spentAt, String description) {
        static ExpenseKey from(Expense expense) {
            return new ExpenseKey(expense.getCategory(), expense.getAmount(), expense.getSpentAt(), expense.getDescription());
        }

        static ExpenseKey from(ExpenseSyncRequest request) {
            return new ExpenseKey(request.category(), request.amount(), request.spentAt(), request.description());
        }
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpenses(UUID userId) {
        return expenseRepository.findByUserId(userId)
            .stream()
            .map(ExpenseResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpensesByPeriod(UUID userId, LocalDate start, LocalDate end) {
        return expenseRepository.findByUserIdAndSpentAtBetween(userId, start, end)
            .stream()
            .map(ExpenseResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExpenseStatsResponse getExpenseStats(UUID userId, LocalDate start, LocalDate end) {
        List<ExpenseCategoryStat> byCategory = expenseRepository.sumByCategory(userId, start, end);
        long totalAmount = byCategory.stream().mapToLong(ExpenseCategoryStat::amount).sum();
        return new ExpenseStatsResponse(totalAmount, byCategory);
    }
}
