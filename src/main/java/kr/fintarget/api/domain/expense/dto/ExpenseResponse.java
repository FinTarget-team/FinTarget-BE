package kr.fintarget.api.domain.expense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.fintarget.api.domain.expense.entity.Expense;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ExpenseResponse(
    @Schema(description = "지출 ID (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID expenseId,
    @Schema(description = "지출 금액(원)", example = "15000")
    Long amount,
    @Schema(description = "지출 카테고리", example = "식비")
    String category,
    @Schema(description = "지출에 대한 메모/설명. 클라이언트가 값을 보내지 않았으면 null", example = "점심 식사", types = {"string", "null"})
    String description,
    @Schema(description = "지출이 발생한 날짜 (yyyy-MM-dd)", example = "2026-08-15")
    LocalDate spentAt,
    @Schema(description = "서버에 동기화(저장)된 시각", example = "2026-08-15T12:30:00")
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
