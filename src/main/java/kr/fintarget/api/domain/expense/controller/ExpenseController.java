package kr.fintarget.api.domain.expense.controller;

import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.expense.dto.ExpenseResponse;
import kr.fintarget.api.domain.expense.dto.ExpenseSyncRequest;
import kr.fintarget.api.domain.expense.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<Void>> syncExpenses(
            @AuthenticationPrincipal String userId,
            @RequestBody List<ExpenseSyncRequest> requests) {
        expenseService.syncExpenses(UUID.fromString(userId), requests);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpenseResponse>>> getExpenses(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getExpenses(UUID.fromString(userId))));
    }

    @GetMapping("/period")
    public ResponseEntity<ApiResponse<List<ExpenseResponse>>> getExpensesByPeriod(
            @AuthenticationPrincipal String userId,
            @RequestParam(name = "start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(name = "end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(
                ApiResponse.ok(expenseService.getExpensesByPeriod(UUID.fromString(userId), start, end))
        );
    }
}
