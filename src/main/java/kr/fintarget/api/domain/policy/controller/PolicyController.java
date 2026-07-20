package kr.fintarget.api.domain.policy.controller;

import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.policy.dto.PolicyResponse;
import kr.fintarget.api.domain.policy.dto.UserPolicyCreateRequest;
import kr.fintarget.api.domain.policy.dto.UserPolicyResponse;
import kr.fintarget.api.domain.policy.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PolicyResponse>>> getMatchingPolicies(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) String policyType) {
        return ResponseEntity.ok(ApiResponse.ok(policyService.getMatchingPolicies(userId, policyType)));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<UserPolicyResponse>>> getUserPolicies(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(policyService.getUserPolicies(UUID.fromString(userId))));
    }

    @PostMapping("/my")
    public ResponseEntity<ApiResponse<UserPolicyResponse>> createUserPolicy(
            @AuthenticationPrincipal String userId,
            @RequestBody UserPolicyCreateRequest request) {
        UserPolicyResponse response = policyService.createUserPolicy(UUID.fromString(userId), request);
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    @DeleteMapping("/my/{userPolicyId}")
    public ResponseEntity<ApiResponse<Void>> deleteUserPolicy(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID userPolicyId) {
        policyService.deleteUserPolicy(UUID.fromString(userId), userPolicyId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}