package kr.fintarget.api.domain.demo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.demo.dto.DemoResetResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 부스 시연용 내 계정 초기화 (#239). demo.reset.enabled=true일 때만 등록되고, 꺼져 있으면 경로가 없어 404다.
 */
@Tag(name = "시연", description = "부스 시연용 계정 초기화 API (demo.reset.enabled=true일 때만 열림)")
@SecurityRequirement(name = "bearerAuth")
@ConditionalOnProperty(name = "demo.reset.enabled", havingValue = "true")
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class DemoResetController {

    private final DemoResetService demoResetService;

    @Operation(
            summary = "내 계정 초기화 (시연용)",
            description = "액세스 토큰으로 식별되는 사용자의 데이터를 모두 지우고 온보딩을 미완료로 되돌립니다. "
                    + "온보딩 답변·프로필 값(닉네임 포함)·목표와 이전 목표·시뮬레이션·저장한 정책·단건 채팅·AI 사용량 기록을 지웁니다. "
                    + "지출(expense)과 넛지·알림은 지우지 않고 그대로 남기며, 남은 건수를 keptExpenses·keptNudges로 응답합니다. "
                    + "계정(사용자 행), 소셜 로그인 연결, 발급된 토큰은 지우지 않으므로 응답 후에도 같은 토큰으로 계속 호출할 수 있습니다. "
                    + "초기화 직후 GET /users/me는 onboardingCompleted=false와 비어 있는 프로필을 반환하므로, 앱은 온보딩 1단계로 이동하면 됩니다. "
                    + "demo.reset.enabled가 false면 이 API는 404입니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "초기화 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content(examples = @ExampleObject(
                            name = "사용자를 찾을 수 없음",
                            value = """
                                    {"status": 400, "message": "사용자를 찾을 수 없습니다.", "data": null}
                                    """
                    ))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(examples = @ExampleObject(
                            name = "인증 필요",
                            value = """
                                    {"status": 401, "message": "인증이 필요합니다. 토큰을 확인해주세요.", "data": null}
                                    """
                    ))
            )
    })
    @PostMapping("/demo-reset")
    public ResponseEntity<ApiResponse<DemoResetResponse>> resetMine(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(DemoResetResponse.of(userId, demoResetService.reset(userId))));
    }
}
