package kr.fintarget.api.domain.expense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ExpenseStatsResponse(
    @Schema(description = "조회 기간 내 전체 지출 합계(원)", example = "320000")
    Long totalAmount,
    @Schema(description = "카테고리별 지출 합계 목록. 조회 기간 내 지출이 없는 카테고리는 포함되지 않습니다.")
    List<ExpenseCategoryStat> byCategory
) {}
