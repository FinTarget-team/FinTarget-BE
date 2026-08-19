package kr.fintarget.api.domain.user;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.user.dto.UpdateProfileRequest;
import kr.fintarget.api.domain.user.dto.UserProfileResponse;
import kr.fintarget.api.domain.user.service.UserService;
import kr.fintarget.api.security.BlacklistedToken;
import kr.fintarget.api.security.BlacklistedTokenRepository;
import kr.fintarget.api.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "사용자", description = "회원 프로필 조회/수정 및 회원 탈퇴 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final BlacklistedTokenRepository blacklistedTokenRepository;

    @Operation(
            summary = "내 프로필 조회",
            description = "액세스 토큰으로 식별되는 사용자의 프로필 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "프로필 조회 성공",
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
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(userId)));
    }

    @Operation(
            summary = "내 프로필 수정",
            description = "이름/이메일/지역을 수정합니다. 요청 필드 중 null인 값은 기존 값을 유지하고 변경하지 않습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "프로필 수정 성공",
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
    @PutMapping("/me/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @AuthenticationPrincipal String userId,
            @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(userId, request)));
    }

    @Operation(
            summary = "회원 탈퇴",
            description = "사용자 및 연관된 목표/지출/정책/시뮬레이션 데이터를 모두 삭제합니다. " +
                    "요청에 포함된 Authorization 헤더의 토큰(또는 바디에 담긴 리프레시 토큰)은 블랙리스트에 등록되어 즉시 무효화됩니다. " +
                    "이 작업은 되돌릴 수 없습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "탈퇴 처리 성공, data는 항상 null",
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
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<?>> withdraw(
            @AuthenticationPrincipal String userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        userService.withdraw(userId);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String jti = jwtUtil.getJti(token);
            if (jti != null && !blacklistedTokenRepository.existsById(jti)) {
                blacklistedTokenRepository.save(new BlacklistedToken(jti, jwtUtil.getExpirationTime(token)));
            }
        }

        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
