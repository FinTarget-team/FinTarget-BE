package kr.fintarget.api.domain.expense.dto;

import kr.fintarget.api.domain.expense.entity.Expense;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ExpenseResponse(
    UUID expenseId,
    Long amount,
    String category,
    String description,
    LocalDate spentAt,
    LocalDateTime createdAt
) {
    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
            expense.getExpenseId(),
            expense.getAmount(),
            expense.getCategory(),
            expense.getDescription(),
            expense.getSpentAt(),
            expense.getCreatedAt()
        );
    }
}
