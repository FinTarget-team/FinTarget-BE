package kr.fintarget.api.domain.goal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.goal.dto.GoalCreateRequest;
import kr.fintarget.api.domain.goal.dto.GoalUpdateRequest;
import kr.fintarget.api.domain.goal.dto.GoalResponse;
import kr.fintarget.api.domain.goal.service.GoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Tag(name = "목표", description = "사용자의 저축 목표 생성/조회/수정/삭제 API. 사용자당 목표는 항상 최대 1개입니다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @Operation(
            summary = "목표 생성",
            description = "사용자의 저축 목표를 생성합니다. 사용자당 목표는 1개만 가질 수 있어, 이미 목표가 있는 상태에서 다시 호출하면 거부됩니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "목표 생성 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰이 없거나 유효하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "인증 실패", value = """
                                    {
                                      "status": 401,
                                      "message": "인증이 필요합니다. 토큰을 확인해주세요.",
                                      "data": null
                                    }
                                    """)
                    })
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 목표를 가지고 있는 사용자가 다시 생성을 시도함",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "목표 중복", value = """
                                    {
                                      "status": 409,
                                      "message": "이미 목표가 존재합니다.",
                                      "data": null
                                    }
                                    """)
                    })
            )
    })
    @PostMapping
    public ResponseEntity<ApiResponse<GoalResponse>> createGoal(
            @AuthenticationPrincipal String userId,
            @RequestBody GoalCreateRequest request) {
        GoalResponse response = goalService.createGoal(UUID.fromString(userId), request);
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    @Operation(
            summary = "내 목표 조회",
            description = "사용자의 저축 목표를 조회합니다. 아직 목표를 만들지 않았다면 에러가 아니라 "
                    + "200 상태 코드에 data: null로 응답합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공. 목표가 없으면 data는 null",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰이 없거나 유효하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "인증 실패", value = """
                                    {
                                      "status": 401,
                                      "message": "인증이 필요합니다. 토큰을 확인해주세요.",
                                      "data": null
                                    }
                                    """)
                    })
            )
    })
    @GetMapping
    public ResponseEntity<ApiResponse<GoalResponse>> getGoal(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(goalService.getGoal(UUID.fromString(userId))));
    }

    @Operation(
            summary = "목표 수정",
            description = "사용자의 저축 목표를 수정합니다. 요청 본문의 모든 필드로 기존 값을 덮어씁니다(부분 수정 아님)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "수정된 목표 반환",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "수정할 목표가 존재하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "목표 없음", value = """
                                    {
                                      "status": 400,
                                      "message": "목표가 존재하지 않습니다.",
                                      "data": null
                                    }
                                    """)
                    })
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰이 없거나 유효하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "인증 실패", value = """
                                    {
                                      "status": 401,
                                      "message": "인증이 필요합니다. 토큰을 확인해주세요.",
                                      "data": null
                                    }
                                    """)
                    })
            )
    })
    @PutMapping
    public ResponseEntity<ApiResponse<GoalResponse>> updateGoal(
            @AuthenticationPrincipal String userId,
            @RequestBody GoalUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(goalService.updateGoal(UUID.fromString(userId), request)));
    }

    @Operation(
            summary = "목표 삭제",
            description = "사용자의 저축 목표를 삭제합니다. 해당 목표에 연결된 시뮬레이션 기록도 함께 삭제됩니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공. data는 항상 null",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "삭제할 목표가 존재하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "목표 없음", value = """
                                    {
                                      "status": 400,
                                      "message": "목표가 존재하지 않습니다.",
                                      "data": null
                                    }
                                    """)
                    })
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 토큰이 없거나 유효하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "인증 실패", value = """
                                    {
                                      "status": 401,
                                      "message": "인증이 필요합니다. 토큰을 확인해주세요.",
                                      "data": null
                                    }
                                    """)
                    })
            )
    })
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteGoal(
            @AuthenticationPrincipal String userId) {
        goalService.deleteGoal(UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
