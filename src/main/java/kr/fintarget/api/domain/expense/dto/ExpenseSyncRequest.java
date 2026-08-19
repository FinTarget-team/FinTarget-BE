package kr.fintarget.api.domain.expense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record ExpenseSyncRequest(
    @Schema(description = "지출 금액(원)", example = "15000")
    Long amount,
    @Schema(description = "지출 카테고리", example = "식비")
    String category,
    @Schema(description = "지출에 대한 메모/설명. 생략하거나 null로 보내도 됩니다.", example = "점심 식사", types = {"string", "null"})
    String description,
    @Schema(description = "지출이 발생한 날짜 (yyyy-MM-dd)", example = "2026-08-15")
    LocalDate spentAt
) {}
