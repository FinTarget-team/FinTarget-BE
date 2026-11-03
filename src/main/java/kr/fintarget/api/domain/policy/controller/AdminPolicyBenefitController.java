package kr.fintarget.api.domain.policy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.policy.benefit.BenefitExtractionResult;
import kr.fintarget.api.domain.policy.benefit.PolicyBenefitExtractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자용 정책 혜택 추출 백필 (#162). 기존 정책을 한 번에 추출하거나, 프롬프트를 바꾼 뒤 다시 추출할 때 쓴다.
 * X-Admin-Key 헤더가 ADMIN_API_KEY와 일치해야 호출할 수 있다 (AdminApiKeyFilter).
 */
@Tag(name = "정책 관리(운영)")
@SecurityRequirement(name = "adminApiKey")
@RestController
@RequestMapping("/api/admin/policy/benefit")
@RequiredArgsConstructor
public class AdminPolicyBenefitController {

    private final PolicyBenefitExtractionService policyBenefitExtractionService;

    @Operation(
            summary = "정책 혜택 AI 추출 (백필)",
            description = "저장된 정책 원문에서 시뮬레이션에 반영할 현금 혜택(월 금액, 기간, 1회 금액)을 AI로 추출해 저장합니다. "
                    + "기본(all=false)은 아직 추출하지 않았거나 원문·프롬프트 버전이 바뀐 정책만 처리하며, 동기화 직후에도 같은 작업이 자동 실행됩니다. "
                    + "all=true면 전체 정책을 다시 추출합니다. KINFA 정책은 AI를 호출하지 않고 LOAN으로 분류합니다. "
                    + "정책 수에 따라 수 분이 걸릴 수 있으며, 완료되면 유형별 저장 건수를 반환합니다. "
                    + "AI를 쓸 수 없는 상태(키 없음, 호출 한도 초과)면 남은 정책을 건너뛰고 skippedCount로 알려줍니다. "
                    + "이미 실행 중이면 409를 반환합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "추출 완료",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 키가 없거나 일치하지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "혜택 추출이 이미 실행 중")
    })
    @PostMapping("/extract")
    public ResponseEntity<ApiResponse<BenefitExtractionResult>> extract(
            @Parameter(description = "true면 이미 추출한 정책까지 전부 다시 추출")
            @RequestParam(defaultValue = "false") boolean all) {
        BenefitExtractionResult result = all
                ? policyBenefitExtractionService.extractAll()
                : policyBenefitExtractionService.extractPending();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
