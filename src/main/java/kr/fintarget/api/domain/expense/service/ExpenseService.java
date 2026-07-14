package kr.fintarget.api.domain.expense.service;

import kr.fintarget.api.domain.expense.dto.ExpenseResponse;
import kr.fintarget.api.domain.expense.dto.ExpenseSyncRequest;
import kr.fintarget.api.domain.expense.entity.Expense;
import kr.fintarget.api.domain.expense.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;

    @Transactional
    public void syncExpenses(UUID userId, List<ExpenseSyncRequest> requests) {
        List<Expense> expenses = requests.stream()
            .map(request -> new Expense(
                userId,
                request.amount(),
                request.category(),
                request.description(),
                request.spentAt()
            ))
            .collect(Collectors.toList());

        expenseRepository.saveAll(expenses);
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
}
