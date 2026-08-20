package kr.fintarget.api.domain.expense.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ExpenseCategoryStat(
    @Schema(description = "지출 카테고리", example = "식비")
    String category,
    @Schema(description = "해당 카테고리의 조회 기간 내 지출 합계(원)", example = "120000")
    Long amount
) {}
