package kr.fintarget.api.domain.expense.dto;

import java.util.List;

public record ExpenseStatsResponse(
    Long totalAmount,
    List<ExpenseCategoryStat> byCategory
) {}
