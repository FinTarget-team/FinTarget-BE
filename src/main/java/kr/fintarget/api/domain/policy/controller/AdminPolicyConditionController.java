package kr.fintarget.api.domain.policy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.policy.condition.ConditionExtractionResult;
import kr.fintarget.api.domain.policy.condition.PolicyConditionExtractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자용 정책 조건 추출 백필 (#220). 배포 직후 기존 정책을 한 번에 추출하거나, 프롬프트를 바꾼 뒤 다시 추출할 때 쓴다.
 * X-Admin-Key 헤더가 ADMIN_API_KEY와 일치해야 호출할 수 있다 (AdminApiKeyFilter).
 */
@Tag(name = "정책 관리(운영)")
@SecurityRequirement(name = "adminApiKey")
@RestController
@RequestMapping("/api/admin/policy/condition")
@RequiredArgsConstructor
public class AdminPolicyConditionController {

    private final PolicyConditionExtractionService policyConditionExtractionService;

    @Operation(
            summary = "정책 신청 조건 AI 추출 (백필)",
            description = "저장된 정책 원문에서 소득 기준·본인 소득 상한, 무주택 필수 여부, 신청 대상(개인/사업자), 신청 가능 지역을 AI로 추출해 저장합니다. "
                    + "추출 결과는 정책 추천에서 확실히 해당하지 않는 정책을 빼고 정렬하는 데만 쓰며, 정책 목록 응답에는 포함되지 않습니다. "
                    + "기본(all=false)은 아직 추출하지 않았거나 원문·프롬프트 버전이 바뀐 정책만 처리하며, 동기화 직후에도 같은 작업이 자동 실행됩니다. "
                    + "all=true면 전체 정책을 다시 추출합니다. 정책 900건 기준 수십 분이 걸릴 수 있습니다. "
                    + "AI를 쓸 수 없는 상태(키 없음, 호출 한도 초과)면 남은 정책을 건너뛰고 skippedCount로 알려줍니다. "
                    + "이미 실행 중이면 409를 반환합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "추출 완료",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 키가 없거나 일치하지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "조건 추출이 이미 실행 중")
    })
    @PostMapping("/extract")
    public ResponseEntity<ApiResponse<ConditionExtractionResult>> extract(
            @Parameter(description = "true면 이미 추출한 정책까지 전부 다시 추출")
            @RequestParam(defaultValue = "false") boolean all) {
        ConditionExtractionResult result = all
                ? policyConditionExtractionService.extractAll()
                : policyConditionExtractionService.extractPending();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
