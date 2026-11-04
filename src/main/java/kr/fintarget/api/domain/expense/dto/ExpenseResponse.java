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
    // iOS ExpenseDTO.description이 non-optional String이라 null이면 목록 전체 디코딩이 실패한다. 저장값이 null이어도 빈 문자열로 내려준다
    @Schema(description = "지출에 대한 메모/설명. 값이 없으면 빈 문자열(\"\")이며 null은 내려가지 않습니다", example = "점심 식사")
    String description,
    @Schema(description = "지출이 발생한 날짜 (yyyy-MM-dd)", example = "2026-08-15")
    LocalDate spentAt,
    @Schema(description = "서버에 동기화(저장)된 시각", example = "2026-08-15T12:30:00")
    LocalDateTime createdAt
) {
    public static ExpenseResponse from(Expense expense) {
        return of(
            expense.getExpenseId(),
            expense.getAmount(),
            expense.getCategory(),
            expense.getDescription(),
            expense.getSpentAt(),
            expense.getCreatedAt()
        );
    }

    public static ExpenseResponse of(UUID expenseId, Long amount, String category, String description,
                                     LocalDate spentAt, LocalDateTime createdAt) {
        return new ExpenseResponse(expenseId, amount, category, description == null ? "" : description, spentAt, createdAt);
    }
}
