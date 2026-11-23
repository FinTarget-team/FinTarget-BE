package kr.fintarget.api.domain.expense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.fintarget.api.domain.expense.entity.Expense;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ExpenseSyncRequest(
    @Schema(description = "지출 금액(원). 0보다 커야 합니다.", example = "15000")
    @NotNull(message = "지출 금액을 입력해주세요.")
    @Positive(message = "지출 금액은 0보다 커야 합니다.")
    @Max(value = Expense.MAX_AMOUNT, message = "지출 금액은 10억 원 이하여야 합니다.")
    Long amount,
    @Schema(description = "지출 카테고리", example = "식비")
    @NotBlank(message = "지출 카테고리를 입력해주세요.")
    @Size(max = Expense.MAX_CATEGORY_LENGTH, message = "지출 카테고리는 20자 이하로 입력해주세요.")
    String category,
    @Schema(description = "지출에 대한 메모/설명. 생략하거나 null로 보내도 됩니다.", example = "점심 식사", types = {"string", "null"})
    @Size(max = Expense.MAX_DESCRIPTION_LENGTH, message = "지출 메모는 255자 이하로 입력해주세요.")
    String description,
    @Schema(description = "지출이 발생한 날짜 (yyyy-MM-dd)", example = "2026-08-15")
    @NotNull(message = "지출 날짜를 입력해주세요.")
    LocalDate spentAt
) {}
