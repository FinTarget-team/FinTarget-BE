package kr.fintarget.api.domain.expense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

public record BankTransactionRequest(
    @Schema(description = "가상 금융 서버의 거래 ID. 처리 결과를 짝지어 돌려주는 데만 쓰며 저장하지 않습니다.", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
    @NotNull(message = "거래 ID를 입력해주세요.")
    UUID transactionId,
    @Schema(description = "핀타겟 사용자 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @NotNull(message = "사용자 ID를 입력해주세요.")
    UUID userId,
    @Schema(description = "지출 금액(원)", example = "12000")
    @NotNull(message = "지출 금액을 입력해주세요.")
    @Positive(message = "지출 금액은 0보다 커야 합니다.")
    Long amount,
    @Schema(description = "지출 카테고리", example = "식비")
    @NotBlank(message = "지출 카테고리를 입력해주세요.")
    String category,
    @Schema(description = "지출 설명(가맹점명 등). 생략 가능", example = "김밥천국")
    String description,
    @Schema(description = "지출 날짜 (yyyy-MM-dd)", example = "2026-11-02")
    @NotNull(message = "지출 날짜를 입력해주세요.")
    LocalDate spentAt
) {}
