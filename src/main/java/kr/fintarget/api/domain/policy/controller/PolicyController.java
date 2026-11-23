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
import jakarta.validation.Valid;
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
            description = "사용자의 나이/소득/거주 지역 조건에 맞는 정책 목록을 조회합니다. policyType을 지정하면 해당 유형으로 추가 필터링합니다 "
                    + "(대소문자 구분 없이 HOUSING, FINANCE_WELFARE, STARTUP, ETC 중 하나여야 함). "
                    + "나이는 출생연도로 계산한 만 나이 후보 두 개(올해-출생연도-1, 올해-출생연도) 중 하나라도 정책 나이 범위에 들면 포함하며, "
                    + "출생연도 입력 전 사용자는 기존 연령대 값으로 비교합니다. "
                    + "지역은 전국 정책과 사용자 거주 지역이 신청 가능 지역(regions)에 포함된 정책만 반환하며, 거주 지역을 입력하지 않은 사용자에게는 전국 정책만 반환합니다. "
                    + "나이나 소득 중 하나만 입력한 사용자는 입력하지 않은 조건만 건너뛰고 매칭하며, 둘 다 입력하지 않은 사용자에게는 빈 배열이 반환됩니다(에러 아님). "
                    + "정책 원문에서 AI로 추출한 신청 조건으로 확실히 해당하지 않는 정책은 제외합니다: 본인 소득 상한을 사용자 소득 구간의 하한도 넘는 경우, "
                    + "무주택 필수 정책인데 사용자가 유주택인 경우, 사업자 전용 정책인데 사용자가 자영업자가 아닌 경우. "
                    + "조건을 알 수 없거나 사용자 정보가 없으면 제외하지 않으며, 부부합산·중위소득·수급자 기준 소득 조건으로는 제외하지 않습니다. "
                    + "신청 가능 지역이 비어 있는 정책은 원문에서 추출한 지역으로 판정합니다(응답 regions 값은 그대로). "
                    + "목록은 지금 신청할 수 있는 정책 → 신청 기간을 판정할 수 없는 정책 → 마감·시작 전 정책 순서이며, "
                    + "같은 순위에서는 사용자 지역 정책, 조건이 확인된 정책 순서로 정렬합니다. "
                    + "이 중 경제적 혜택(현금 지급·적립 매칭·이자·보증료·실비 지원, 바우처·할인·저렴한 임대)이 있는 정책 상위 10건만 반환하며, "
                    + "이런 정책으로 10건을 채우지 못하면 일반 대출 상품으로 채웁니다. 상담·교육·행사·공간 운영 같은 정책과 혜택 유형을 알 수 없는 정책은 포함하지 않고, "
                    + "이름이 비슷한 정책은 2건까지만 넣습니다. 응답 형식은 바뀌지 않으며 목록 건수와 순서만 달라집니다."
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
                    description = "policyId·status 누락, status가 허용된 값(APPLIED, INTEREST, ABANDONED, 대소문자 무관)이 아님, 또는 policyId에 해당하는 정책이 존재하지 않음",
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
                                      "message": "유효하지 않은 정책 상태입니다. (APPLIED, INTEREST, ABANDONED 중 하나)",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "status 누락", value = """
                                    {
                                      "status": 400,
                                      "message": "정책 상태를 입력해주세요.",
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
            @Valid @RequestBody UserPolicyCreateRequest request) {
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
                    description = "userPolicyId에 해당하는 스크랩이 없거나 다른 사용자의 스크랩임 (존재 여부를 드러내지 않도록 같은 응답)",
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