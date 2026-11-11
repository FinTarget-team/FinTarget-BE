package kr.fintarget.api.domain.expense.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.expense.dto.BankTransactionRequest;
import kr.fintarget.api.domain.expense.dto.BankTransactionResult;
import kr.fintarget.api.domain.expense.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 가상 금융 서버(mock-bank)가 발생시킨 거래를 받는 서버 간 웹훅.
 * 사용자 JWT 대신 X-Bank-Webhook-Key 헤더로 인증한다 (BankWebhookKeyFilter).
 */
@Tag(name = "은행 거래 웹훅(서버 간)", description = "가상 금융 서버가 발생시킨 거래를 지출로 받는 API. 사용자 JWT가 아니라 X-Bank-Webhook-Key 헤더로 인증합니다. 앱(프론트)에서는 호출하지 않습니다.")
@SecurityRequirement(name = "bankWebhookKey")
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class BankWebhookController {

    static final int MAX_TRANSACTIONS_PER_REQUEST = 1000;

    private final ExpenseService expenseService;
    private final Validator validator;

    @Operation(
            summary = "은행 거래 수신",
            description = "거래 목록을 받아 각 거래의 사용자 지출로 저장하고, 거래별 처리 결과를 요청 순서대로 반환합니다. "
                    + "같은 사용자의 (카테고리, 금액, 날짜, 설명)이 같은 지출이 이미 있거나 같은 요청 안에서 앞서 나왔으면 저장하지 않고 DUPLICATE를 반환하므로, "
                    + "같은 거래를 다시 보내도 안전합니다. "
                    + "항목 검증에 실패하거나 존재하지 않는 사용자가 하나라도 있으면 아무것도 저장하지 않고 400을 반환합니다. "
                    + "한 번에 최대 " + MAX_TRANSACTIONS_PER_REQUEST + "건까지 보낼 수 있고, 빈 배열이면 빈 결과를 반환합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "처리 완료. data는 거래별 결과(CREATED / DUPLICATE) 목록",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "항목 검증 실패(transactionId·userId·amount·category·spentAt 누락, amount 0 이하, null 항목), 건수 초과, 존재하지 않는 사용자. 이때 목록 전체를 저장하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "존재하지 않는 사용자", value = """
                                    {
                                      "status": 400,
                                      "message": "존재하지 않는 사용자입니다: 3fa85f64-5717-4562-b3fc-2c963f66afa6",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "금액 0 이하", value = """
                                    {
                                      "status": 400,
                                      "message": "지출 금액은 0보다 커야 합니다.",
                                      "data": null
                                    }
                                    """)
                    })
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "웹훅 키가 없거나 일치하지 않음, 또는 서버에 웹훅 키가 설정되지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "웹훅 키 불일치", value = """
                                    {
                                      "status": 403,
                                      "message": "웹훅 키가 올바르지 않습니다.",
                                      "data": null
                                    }
                                    """)
                    })
            )
    })
    @PostMapping("/bank-transactions")
    public ResponseEntity<ApiResponse<List<BankTransactionResult>>> receiveBankTransactions(
            @RequestBody List<BankTransactionRequest> requests) {
        validateRequests(requests);
        return ResponseEntity.ok(ApiResponse.ok(expenseService.receiveBankTransactions(requests)));
    }

    // ExpenseController.validateSyncRequests와 같은 이유로, 목록 원소는 직접 검증해 400(IllegalArgumentException)으로 맞춘다
    private void validateRequests(List<BankTransactionRequest> requests) {
        if (requests.size() > MAX_TRANSACTIONS_PER_REQUEST) {
            throw new IllegalArgumentException("거래는 한 번에 최대 " + MAX_TRANSACTIONS_PER_REQUEST + "건까지 보낼 수 있습니다.");
        }
        for (BankTransactionRequest request : requests) {
            if (request == null) {
                throw new IllegalArgumentException("거래 목록에 빈 항목(null)이 있습니다.");
            }
            validator.validate(request).stream()
                    .findFirst()
                    .map(ConstraintViolation::getMessage)
                    .ifPresent(message -> {
                        throw new IllegalArgumentException(message);
                    });
        }
    }
}
