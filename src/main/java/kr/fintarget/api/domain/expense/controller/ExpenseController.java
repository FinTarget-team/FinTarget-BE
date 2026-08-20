package kr.fintarget.api.domain.expense.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.expense.dto.ExpenseResponse;
import kr.fintarget.api.domain.expense.dto.ExpenseStatsResponse;
import kr.fintarget.api.domain.expense.dto.ExpenseSyncRequest;
import kr.fintarget.api.domain.expense.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "지출", description = "사용자의 지출 내역 동기화, 조회, 기간별 조회, 카테고리별 통계 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @Operation(
            summary = "지출 내역 동기화",
            description = "클라이언트가 보유한 지출 내역 목록을 서버에 동기화합니다. "
                    + "(카테고리, 금액, 지출일, 설명) 조합이 이미 저장되어 있으면 건너뛰고 새로운 항목만 추가하는 "
                    + "멱등(idempotent) 방식이라, 같은 목록을 여러 번 보내도 중복 저장되지 않습니다. "
                    + "빈 배열을 보내면 아무 작업도 하지 않고 성공을 반환합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "동기화 완료. data는 항상 null",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰이 없거나 유효하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "인증 실패", value = """
                                    {
                                      "status": 401,
                                      "message": "인증이 필요합니다. 토큰을 확인해주세요.",
                                      "data": null
                                    }
                                    """)
                    })
            )
    })
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<Void>> syncExpenses(
            @AuthenticationPrincipal String userId,
            @RequestBody List<ExpenseSyncRequest> requests) {
        expenseService.syncExpenses(UUID.fromString(userId), requests);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(
            summary = "전체 지출 내역 조회",
            description = "사용자의 모든 지출 내역을 조회합니다. 지출 내역이 없으면 빈 배열을 반환합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "지출 내역 목록 조회 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰이 없거나 유효하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "인증 실패", value = """
                                    {
                                      "status": 401,
                                      "message": "인증이 필요합니다. 토큰을 확인해주세요.",
                                      "data": null
                                    }
                                    """)
                    })
            )
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpenseResponse>>> getExpenses(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getExpenses(UUID.fromString(userId))));
    }

    @Operation(
            summary = "기간별 지출 내역 조회",
            description = "start~end 기간에 해당하는 지출 내역을 조회합니다. "
                    + "start가 end보다 늦은 값이 들어와도 별도 검증 없이 빈 배열이 반환됩니다 "
                    + "(같은 파라미터를 쓰는 카테고리별 통계 조회(/stats)와 달리 400으로 거부하지 않습니다)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "기간 내 지출 내역 목록 조회 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰이 없거나 유효하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "인증 실패", value = """
                                    {
                                      "status": 401,
                                      "message": "인증이 필요합니다. 토큰을 확인해주세요.",
                                      "data": null
                                    }
                                    """)
                    })
            )
    })
    @GetMapping("/period")
    public ResponseEntity<ApiResponse<List<ExpenseResponse>>> getExpensesByPeriod(
            @AuthenticationPrincipal String userId,
            @Parameter(description = "조회 시작일 (yyyy-MM-dd)", required = true, example = "2026-08-01")
            @RequestParam(name = "start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @Parameter(description = "조회 종료일 (yyyy-MM-dd)", required = true, example = "2026-08-31")
            @RequestParam(name = "end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(
                ApiResponse.ok(expenseService.getExpensesByPeriod(UUID.fromString(userId), start, end))
        );
    }

    @Operation(
            summary = "기간별 카테고리 통계 조회",
            description = "start~end 기간의 카테고리별 지출 합계와 전체 합계를 반환합니다. "
                    + "start가 end보다 늦으면 400으로 거부됩니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "카테고리별 통계 조회 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "start가 end보다 늦음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "잘못된 기간", value = """
                                    {
                                      "status": 400,
                                      "message": "시작일이 종료일보다 늦을 수 없습니다.",
                                      "data": null
                                    }
                                    """)
                    })
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰이 없거나 유효하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "인증 실패", value = """
                                    {
                                      "status": 401,
                                      "message": "인증이 필요합니다. 토큰을 확인해주세요.",
                                      "data": null
                                    }
                                    """)
                    })
            )
    })
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ExpenseStatsResponse>> getExpenseStats(
            @AuthenticationPrincipal String userId,
            @Parameter(description = "통계 집계 시작일 (yyyy-MM-dd)", required = true, example = "2026-08-01")
            @RequestParam(name = "start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @Parameter(description = "통계 집계 종료일 (yyyy-MM-dd)", required = true, example = "2026-08-31")
            @RequestParam(name = "end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(
                ApiResponse.ok(expenseService.getExpenseStats(UUID.fromString(userId), start, end))
        );
    }
}
