package kr.fintarget.api.domain.policy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.policy.chunk.PolicyChunkRefreshResult;
import kr.fintarget.api.domain.policy.service.PolicySyncOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자용 정책 청크 백필 (#180). 배포 직후 다음 동기화(매일 03:00)를 기다리지 않고 기존 정책의 청크를 만들거나,
 * 분할 규칙을 바꾼 뒤 다시 만들 때 쓴다. X-Admin-Key 헤더가 ADMIN_API_KEY와 일치해야 호출할 수 있다 (AdminApiKeyFilter).
 */
@Tag(name = "정책 관리(운영)")
@SecurityRequirement(name = "adminApiKey")
@RestController
@RequestMapping("/api/admin/policy/chunk")
@RequiredArgsConstructor
public class AdminPolicyChunkController {

    private final PolicySyncOrchestrator policySyncOrchestrator;

    @Operation(
            summary = "정책 원문 청크 갱신 (백필)",
            description = "전체 정책 검색에 쓸 정책 원문 청크를 만듭니다. "
                    + "기본(all=false)은 청크가 없거나 원문·분할 규칙이 바뀐 정책만 다시 만들며, 동기화 직후에도 같은 작업이 자동 실행됩니다. "
                    + "all=true면 전체 정책의 청크를 다시 만듭니다. AI를 호출하지 않습니다. "
                    + "정책 동기화와 동시에 실행되지 않으며, 동기화나 다른 청크 갱신이 실행 중이면 409를 반환합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "갱신 완료",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 키가 없거나 일치하지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "정책 동기화 또는 청크 갱신이 이미 실행 중")
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<PolicyChunkRefreshResult>> refresh(
            @Parameter(description = "true면 원문이 같은 정책까지 전부 다시 만듦")
            @RequestParam(defaultValue = "false") boolean all) {
        return ResponseEntity.ok(ApiResponse.ok(policySyncOrchestrator.refreshChunksNow(all)));
    }
}
