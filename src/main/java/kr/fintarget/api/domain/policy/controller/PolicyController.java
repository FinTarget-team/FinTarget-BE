package kr.fintarget.api.domain.policy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.policy.dto.PolicyResponse;
import kr.fintarget.api.domain.policy.dto.UserPolicyCreateRequest;
import kr.fintarget.api.domain.policy.dto.UserPolicyResponse;
import kr.fintarget.api.domain.policy.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@Tag(name = "정책", description = "청년 지원 정책 조회 및 사용자별 정책 스크랩(관심/신청/포기 상태 관리) API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    @Operation(
            summary = "나에게 맞는 정책 목록 조회",
            description = "사용자의 나이/소득 조건에 맞는 정책 목록을 조회합니다. policyType을 지정하면 해당 유형으로 추가 필터링합니다 "
                    + "(대소문자 구분 없이 HOUSING, FINANCE_WELFARE, STARTUP, ETC 중 하나여야 함). "
                    + "온보딩에서 나이 또는 소득을 아직 입력하지 않은 사용자는 조건 매칭이 불가능하므로 빈 배열이 반환됩니다(에러 아님)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조건에 맞는 정책 목록 조회 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "인증된 사용자 ID에 해당하는 유저가 존재하지 않거나, policyType이 허용된 값이 아님",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "사용자 없음", value = """
                                    {
                                      "status": 400,
                                      "message": "사용자를 찾을 수 없습니다.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "허용되지 않는 policyType 값 (예시: \\\"FOOD\\\"를 보낸 경우)", value = """
                                    {
                                      "status": 400,
                                      "message": "유효하지 않은 정책 유형입니다: FOOD",
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
    @GetMapping
    public ResponseEntity<ApiResponse<List<PolicyResponse>>> getMatchingPolicies(
            @AuthenticationPrincipal String userId,
            @Parameter(
                    description = "정책 유형으로 추가 필터링. 생략하면 전체 유형 대상으로 조회합니다.",
                    required = false,
                    example = "HOUSING",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"HOUSING", "FINANCE_WELFARE", "STARTUP", "ETC"})
            )
            @RequestParam(required = false) String policyType) {
        return ResponseEntity.ok(ApiResponse.ok(policyService.getMatchingPolicies(userId, policyType)));
    }

    @Operation(
            summary = "내가 스크랩한 정책 목록 조회",
            description = "사용자가 관심/신청/포기 등으로 등록해 둔 정책 목록을 조회합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "스크랩한 정책 목록 조회 성공",
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
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<UserPolicyResponse>>> getUserPolicies(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(policyService.getUserPolicies(UUID.fromString(userId))));
    }

    @Operation(
            summary = "정책 스크랩 등록",
            description = "지정한 정책을 사용자의 관심/신청/포기 목록에 등록합니다. 같은 정책을 중복으로 등록할 수 없습니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "등록 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "policyId에 해당하는 정책이 존재하지 않거나, status 값이 허용된 값(APPLIED, INTEREST, ABANDONED)이 아님",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "존재하지 않는 정책", value = """
                                    {
                                      "status": 400,
                                      "message": "존재하지 않는 정책입니다.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "허용되지 않는 status 값 (예시: \\\"PENDING\\\"을 보낸 경우)", value = """
                                    {
                                      "status": 400,
                                      "message": "No enum constant kr.fintarget.api.domain.policy.entity.UserPolicy.UserPolicyStatus.PENDING",
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 등록된 정책을 다시 등록하려 함",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "중복 등록", value = """
                                    {
                                      "status": 409,
                                      "message": "이미 등록된 정책입니다.",
                                      "data": null
                                    }
                                    """)
                    })
            )
    })
    @PostMapping("/my")
    public ResponseEntity<ApiResponse<UserPolicyResponse>> createUserPolicy(
            @AuthenticationPrincipal String userId,
            @RequestBody UserPolicyCreateRequest request) {
        UserPolicyResponse response = policyService.createUserPolicy(UUID.fromString(userId), request);
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    @Operation(
            summary = "정책 스크랩 삭제",
            description = "사용자가 등록해 둔 정책 스크랩을 삭제합니다. 본인이 등록한 것이 아니면 거부됩니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공. data는 항상 null",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "userPolicyId에 해당하는 스크랩이 존재하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "존재하지 않는 정책", value = """
                                    {
                                      "status": 400,
                                      "message": "존재하지 않는 정책입니다.",
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "본인이 등록하지 않은 다른 사용자의 정책 스크랩을 삭제하려 함",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "소유권 없음", value = """
                                    {
                                      "status": 409,
                                      "message": "본인의 정책만 삭제할 수 있습니다.",
                                      "data": null
                                    }
                                    """)
                    })
            )
    })
    @DeleteMapping("/my/{userPolicyId}")
    public ResponseEntity<ApiResponse<Void>> deleteUserPolicy(
            @AuthenticationPrincipal String userId,
            @Parameter(description = "삭제할 사용자 정책 스크랩 ID (UUID)", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID userPolicyId) {
        policyService.deleteUserPolicy(UUID.fromString(userId), userPolicyId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}