package kr.fintarget.api.domain.goal.controller;

import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.goal.dto.GoalCreateRequest;
import kr.fintarget.api.domain.goal.dto.GoalUpdateRequest;
import kr.fintarget.api.domain.goal.dto.GoalResponse;
import kr.fintarget.api.domain.goal.service.GoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @PostMapping
    public ResponseEntity<ApiResponse<GoalResponse>> createGoal(
            @AuthenticationPrincipal String userId,
            @RequestBody GoalCreateRequest request) {
        GoalResponse response = goalService.createGoal(UUID.fromString(userId), request);
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GoalResponse>> getGoal(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(goalService.getGoal(UUID.fromString(userId))));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<GoalResponse>> updateGoal(
            @AuthenticationPrincipal String userId,
            @RequestBody GoalUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(goalService.updateGoal(UUID.fromString(userId), request)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteGoal(
            @AuthenticationPrincipal String userId) {
        goalService.deleteGoal(UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
