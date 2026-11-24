package kr.fintarget.api.domain.demo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.demo.dto.DemoResetResponse;
import kr.fintarget.api.domain.demo.dto.DemoUserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 부스 시연 비상용 관리자 API (#239). 앱의 초기화 버튼이 안 될 때 노트북에서 호출한다.
 * X-Admin-Key 헤더가 ADMIN_API_KEY와 일치해야 하고(AdminApiKeyFilter), demo.reset.enabled=true일 때만 등록된다.
 */
@Tag(name = "시연(운영)", description = "부스 시연 비상용 관리자 API (demo.reset.enabled=true일 때만 열림)")
@SecurityRequirement(name = "adminApiKey")
@ConditionalOnProperty(name = "demo.reset.enabled", havingValue = "true")
@RestController
@RequestMapping("/api/admin/demo")
@RequiredArgsConstructor
public class AdminDemoController {

    private final DemoResetService demoResetService;

    @Operation(
            summary = "시연 계정 목록 (비상용)",
            description = "초기화할 사용자를 고르기 위한 목록입니다. 사용자 ID, 닉네임, 로그인 방식, 온보딩 완료 여부만 내려주며 "
                    + "이메일·소셜 ID·토큰 같은 민감 정보는 포함하지 않습니다. 가입 순으로 정렬합니다."
    )
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<DemoUserSummary>>> users() {
        return ResponseEntity.ok(ApiResponse.ok(demoResetService.users()));
    }

    @Operation(
            summary = "시연 계정 초기화 (비상용)",
            description = "지정한 사용자의 데이터를 지우고 온보딩을 미완료로 되돌립니다. 내 계정 초기화(POST /api/users/me/demo-reset)와 같은 로직이며, "
                    + "계정·소셜 로그인 연결·발급된 토큰은 지우지 않습니다."
    )
    @PostMapping("/users/{userId}/reset")
    public ResponseEntity<ApiResponse<DemoResetResponse>> reset(
            @Parameter(description = "초기화할 사용자 ID (목록 API의 userId)", required = true)
            @PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.ok(DemoResetResponse.of(userId, demoResetService.reset(userId))));
    }
}
