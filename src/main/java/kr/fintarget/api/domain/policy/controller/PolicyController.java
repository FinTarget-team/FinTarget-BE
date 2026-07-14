package kr.fintarget.api.domain.policy.controller;

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
    public ResponseEntity<?> getMatchingPolicies(
            @RequestParam(required = false) Integer age,
            @RequestParam(required = false) Long income,
            @RequestParam(required = false) String policyType) {
        if (age == null || income == null) {
            return ResponseEntity.badRequest().body("age와 income 파라미터는 필수입니다.");
        }
        return ResponseEntity.ok(policyService.getMatchingPolicies(age, income, policyType));
    }

    @GetMapping("/my")
    public ResponseEntity<List<UserPolicyResponse>> getUserPolicies(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(policyService.getUserPolicies(UUID.fromString(userId)));
    }

    @PostMapping("/my")
    public ResponseEntity<UserPolicyResponse> createUserPolicy(
            @AuthenticationPrincipal String userId,
            @RequestBody UserPolicyCreateRequest request) {
        return ResponseEntity.ok(
                policyService.createUserPolicy(UUID.fromString(userId), request)
        );
    }

    @DeleteMapping("/my/{userPolicyId}")
    public ResponseEntity<Void> deleteUserPolicy(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID userPolicyId) {
        policyService.deleteUserPolicy(UUID.fromString(userId), userPolicyId);
        return ResponseEntity.noContent().build();
    }
}