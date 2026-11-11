package kr.fintarget.api.domain.nudge;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.nudge.dto.NudgeListResponse;
import kr.fintarget.api.domain.nudge.service.NudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "넛지", description = "목표 마감 임박/이상 지출 감지 알림(넛지) 조회 및 읽음/삭제 처리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/nudges")
@RequiredArgsConstructor
public class NudgeController {
    private final NudgeService nudgeService;

    @Operation(
            summary = "넛지 목록 조회",
            description = "조회 시점에 목표 마감 임박(D-30 이내, 하루 1회) 및 이상 지출(평소 대비 150% 이상, 카테고리별 하루 1회) 넛지를 새로 생성한 뒤, " +
                    "사용자의 전체 넛지 목록을 최신순으로 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "넛지 목록 조회 성공",
                    useReturnTypeSchema = true
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
    @GetMapping
    public ResponseEntity<ApiResponse<NudgeListResponse>> getNudges(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(nudgeService.getNudges(userId)));
    }

    @Operation(
            summary = "넛지 읽음 처리",
            description = "지정한 넛지를 읽음 상태로 변경합니다. 본인 소유가 아닌 넛지에 접근하면 401로 거부됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "읽음 처리 성공, data는 항상 null",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content(examples = @ExampleObject(
                            name = "넛지를 찾을 수 없음",
                            value = """
                                    {"status": 400, "message": "넛지를 찾을 수 없습니다.", "data": null}
                                    """
                    ))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 또는 접근 권한 없음",
                    content = @Content(examples = {
                            @ExampleObject(
                                    name = "인증 필요",
                                    value = """
                                            {"status": 401, "message": "인증이 필요합니다. 토큰을 확인해주세요.", "data": null}
                                            """
                            ),
                            @ExampleObject(
                                    name = "다른 사용자의 넛지에 접근",
                                    value = """
                                            {"status": 401, "message": "접근 권한이 없습니다.", "data": null}
                                            """
                            )
                    })
            )
    })
    @PatchMapping("/{nudgeId}/read")
    public ResponseEntity<ApiResponse<?>> markAsRead(
            @AuthenticationPrincipal String userId,
            @Parameter(description = "읽음 처리할 넛지 ID", required = true) @PathVariable String nudgeId) {
        nudgeService.markAsRead(userId, nudgeId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(
            summary = "넛지 삭제",
            description = "지정한 넛지를 삭제합니다. 본인 소유가 아닌 넛지에 접근하면 401로 거부됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공, data는 항상 null",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content(examples = @ExampleObject(
                            name = "넛지를 찾을 수 없음",
                            value = """
                                    {"status": 400, "message": "넛지를 찾을 수 없습니다.", "data": null}
                                    """
                    ))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 또는 접근 권한 없음",
                    content = @Content(examples = {
                            @ExampleObject(
                                    name = "인증 필요",
                                    value = """
                                            {"status": 401, "message": "인증이 필요합니다. 토큰을 확인해주세요.", "data": null}
                                            """
                            ),
                            @ExampleObject(
                                    name = "다른 사용자의 넛지에 접근",
                                    value = """
                                            {"status": 401, "message": "접근 권한이 없습니다.", "data": null}
                                            """
                            )
                    })
            )
    })
    @DeleteMapping("/{nudgeId}")
    public ResponseEntity<ApiResponse<?>> deleteNudge(
            @AuthenticationPrincipal String userId,
            @Parameter(description = "삭제할 넛지 ID", required = true) @PathVariable String nudgeId) {
        nudgeService.deleteNudge(userId, nudgeId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
