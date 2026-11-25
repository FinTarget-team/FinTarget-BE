package kr.fintarget.api.domain.policy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.policy.manual.ManualPolicySyncResult;
import kr.fintarget.api.domain.policy.manual.ManualPolicySyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자용 직접 등록 정책 동기화 (#245). 공공 API로 수집되지 않는 정책(src/main/resources/policy/manual-policies.json)을 등록·갱신한다.
 * X-Admin-Key 헤더가 ADMIN_API_KEY와 일치해야 호출할 수 있다 (AdminApiKeyFilter).
 */
@Tag(name = "정책 관리(운영)")
@SecurityRequirement(name = "adminApiKey")
@RestController
@RequestMapping("/api/admin/policy/manual")
@RequiredArgsConstructor
public class AdminPolicyManualController {

    private final ManualPolicySyncService manualPolicySyncService;

    @Operation(
            summary = "직접 등록 정책 동기화",
            description = "공공 API(온통청년, KINFA)로 수집되지 않는 정책을 서버 리소스 파일(policy/manual-policies.json)에서 읽어 "
                    + "외부 정책 ID(MANUAL-...)로 등록하거나 갱신합니다. 여러 번 실행해도 결과가 같습니다. "
                    + "등록 후 정책 동기화와 같은 순서로 청크 분할, 혜택 추출, 조건 추출, 리랭킹 미리 계산, 임베딩을 실행합니다. "
                    + "시뮬레이션 혜택 값(유형·월 금액·개월 수·1회 금액·최대 기준)은 AI 추출 결과 대신 리소스 값을 저장하며, 다시 추출해도 유지됩니다. "
                    + "출처가 MANUAL이라 매일 03:00 동기화가 지우거나 덮어쓰지 않습니다. 앱 시작 시 자동 실행하지 않으므로 배포 후 한 번 호출합니다. "
                    + "AI 호출이 있어 수십 초가 걸릴 수 있으며, 이미 실행 중이면 409를 반환합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "동기화 완료",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 키가 없거나 일치하지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "직접 등록 정책 동기화가 이미 실행 중")
    })
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<ManualPolicySyncResult>> sync() {
        return ResponseEntity.ok(ApiResponse.ok(manualPolicySyncService.sync()));
    }
}
