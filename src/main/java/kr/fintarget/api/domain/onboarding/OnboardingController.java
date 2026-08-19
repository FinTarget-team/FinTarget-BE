package kr.fintarget.api.domain.onboarding;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.onboarding.dto.OnboardingAnswerRequest;
import kr.fintarget.api.domain.onboarding.dto.OnboardingStepResponse;
import kr.fintarget.api.domain.onboarding.service.OnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "온보딩", description = "신규 사용자의 온보딩 질문 단계 조회 및 답변 제출 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/onboarding")
@RequiredArgsConstructor
public class OnboardingController {
    private final OnboardingService onboardingService;

    @Operation(
            summary = "온보딩 질문 단계 조회",
            description = "step 파라미터(1~5)에 해당하는 온보딩 질문/선택지를 반환합니다. " +
                    "step을 생략하면 1단계가 조회됩니다. 1~5 범위를 벗어난 step 값은 400으로 거부됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "온보딩 질문 단계 조회 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content(examples = @ExampleObject(
                            name = "범위를 벗어난 step",
                            value = """
                                    {"status": 400, "message": "잘못된 단계입니다.", "data": null}
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
    @GetMapping("/step")
    public ResponseEntity<ApiResponse<OnboardingStepResponse>> getStep(
            @Parameter(description = "조회할 온보딩 단계 (1~5, 생략 시 1)", required = false, example = "1")
            @RequestParam(defaultValue = "1") int step) {
        return ResponseEntity.ok(ApiResponse.ok(onboardingService.getStep(step)));
    }

    @Operation(
            summary = "온보딩 답변 제출",
            description = "현재 단계의 답변을 저장하고 다음 단계 질문을 반환합니다. " +
                    "마지막 단계(5단계) 답변 제출 시 온보딩이 완료 처리되며, 응답의 isComplete가 true로 반환됩니다. " +
                    "이전 단계를 완료하지 않고 다음 단계에 답변을 제출하면 400으로 거부됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "답변 제출 성공, 다음 단계 질문 또는 완료 응답 반환",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content(examples = {
                            @ExampleObject(
                                    name = "요청 값 없음",
                                    value = """
                                            {"status": 400, "message": "요청 값이 없습니다", "data": null}
                                            """
                            ),
                            @ExampleObject(
                                    name = "답변 값 누락",
                                    value = """
                                            {"status": 400, "message": "답변 값을 입력해주세요", "data": null}
                                            """
                            ),
                            @ExampleObject(
                                    name = "유효하지 않은 단계 번호",
                                    value = """
                                            {"status": 400, "message": "유효하지 않은 온보딩 단계입니다", "data": null}
                                            """
                            ),
                            @ExampleObject(
                                    name = "이전 단계 미완료",
                                    value = """
                                            {"status": 400, "message": "이전 온보딩 단계를 먼저 완료해주세요", "data": null}
                                            """
                            ),
                            @ExampleObject(
                                    name = "유효하지 않은 나이 코드 (1단계)",
                                    value = """
                                            {"status": 400, "message": "유효하지 않은 나이 코드입니다", "data": null}
                                            """
                            ),
                            @ExampleObject(
                                    name = "유효하지 않은 소득 코드 (3단계)",
                                    value = """
                                            {"status": 400, "message": "유효하지 않은 소득 코드입니다", "data": null}
                                            """
                            ),
                            @ExampleObject(
                                    name = "유저를 찾을 수 없음",
                                    value = """
                                            {"status": 400, "message": "유저를 찾을 수 없습니다.", "data": null}
                                            """
                            )
                    })
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
    @PostMapping("/answer")
    public ResponseEntity<ApiResponse<OnboardingStepResponse>> submitAnswer(
            @AuthenticationPrincipal String userId,
            @RequestBody OnboardingAnswerRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(onboardingService.submitAnswer(userId, request)));
    }
}
