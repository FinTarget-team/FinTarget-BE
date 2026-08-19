package kr.fintarget.api.domain.policy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "정책 관리(운영)", description = "외부 정책 데이터(온통청년, KINFA) 수동 동기화 트리거 API. 운영/배치 목적이며, 별도 관리자 권한 체계는 없습니다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/policy")
@RequiredArgsConstructor
public class AdminPolicyController {

    private final PolicySyncOrchestrator policySyncOrchestrator;

    @Operation(
            summary = "정책 데이터 수동 동기화",
            description = "온통청년(청년정책)과 KINFA(서민금융상품) 외부 API에서 정책 데이터를 가져와 신규 정책만 저장합니다. "
                    + "스케줄러가 주기적으로 수행하는 것과 같은 동작을 즉시 1회 실행합니다. "
                    + "두 소스는 서로 독립적으로 실행되어 한쪽 API가 장애/오류 상태여도 다른 쪽 동기화는 계속 진행됩니다. "
                    + "소스별 동기화 실패 여부는 응답 바디의 youthCenterFailed/kinfaFailed로만 표현되며, "
                    + "이 경우에도 HTTP 상태 코드는 항상 200입니다(즉, 이 엔드포인트는 실질적으로 실패 응답 코드가 없습니다). "
                    + "주의: 이름은 '관리자용'이지만 실제 권한 체계(role)가 구현되어 있지 않아, 인증된 사용자라면 누구나 호출할 수 있습니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "동기화 실행 완료(소스별 성공/실패는 응답 바디로 확인해야 함)",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰이 없거나 유효하지 않음",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                            examples = {
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "인증 실패", value = """
                                            {
                                              "status": 401,
                                              "message": "인증이 필요합니다. 토큰을 확인해주세요.",
                                              "data": null
                                            }
                                            """)
                            }
                    )
            )
    })
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<PolicySyncResult>> syncPolicies() {
        return ResponseEntity.ok(ApiResponse.ok(policySyncOrchestrator.syncAll()));
    }
}
