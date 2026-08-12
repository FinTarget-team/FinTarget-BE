package kr.fintarget.api.domain.policy.controller;

import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.policy.service.PolicySyncOrchestrator;
import kr.fintarget.api.domain.policy.service.PolicySyncResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자용 정책 동기화 수동 트리거. 스케줄러 활성화 여부와 무관하게 즉시 실행된다.
 * 별도 관리자 권한 체계가 아직 없어(User에 role 없음), SecurityConfig의 기본 규칙(anyRequest().authenticated())으로
 * 최소한 인증된 사용자만 호출 가능하도록 제한한다.
 */
@RestController
@RequestMapping("/api/admin/policy")
@RequiredArgsConstructor
public class AdminPolicyController {

    private final PolicySyncOrchestrator policySyncOrchestrator;

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<PolicySyncResult>> syncPolicies() {
        return ResponseEntity.ok(ApiResponse.ok(policySyncOrchestrator.syncAll()));
    }
}
