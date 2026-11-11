package kr.fintarget.api.domain.expense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record BankTransactionResult(
    @Schema(description = "요청에 담겨 온 가상 금융 서버의 거래 ID", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
    UUID transactionId,
    @Schema(description = "CREATED: 새 지출로 저장됨, DUPLICATE: 같은 사용자의 (카테고리, 금액, 날짜, 설명)이 같은 지출이 이미 있어 건너뜀", example = "CREATED")
    Result result
) {
    public enum Result {
        CREATED,
        DUPLICATE
    }
}
