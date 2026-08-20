package kr.fintarget.api.domain.simulation.controller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.simulation.dto.SimulationRequest;
import kr.fintarget.api.domain.simulation.dto.SimulationResponse;
import kr.fintarget.api.domain.simulation.service.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@Tag(name = "시뮬레이션", description = "저축 목표 달성 시점을 복리 계산과 몬테카를로 확률 시뮬레이션으로 예측하는 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/simulations")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    @Operation(
            summary = "목표 달성 시뮬레이션 실행",
            description = "월 저축액, (선택) 연이율, (선택) 반영할 정책을 바탕으로 1만 회 몬테카를로 시뮬레이션을 실행해 "
                    + "목표 달성 예상일(중앙값/낙관/비관)과 목표 마감일까지의 달성 확률을 계산하고, 그 결과를 저장한 뒤 반환합니다. "
                    + "annualInterestRate를 생략하면 연 3%(0.03)가 기본으로 적용됩니다. "
                    + "userPolicyId를 함께 보내면 해당 정책의 혜택 금액을 월 저축액에 더해 반영한 예상 달성일도 함께 계산됩니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "시뮬레이션 실행 및 저장 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 값이 유효성 검증을 통과하지 못했거나, goalId/userPolicyId에 해당하는 리소스가 존재하지 않거나, "
                            + "연이율이 유한한 범위를 벗어남. 아래 예시 중 하나가 message로 반환됨(여러 항목이 동시에 잘못됐어도 하나만 반환됨)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "목표 미선택", value = """
                                    {
                                      "status": 400,
                                      "message": "목표를 선택해주세요.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "월 저축액 미입력", value = """
                                    {
                                      "status": 400,
                                      "message": "월 저축액을 입력해주세요.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "월 저축액이 0 이하", value = """
                                    {
                                      "status": 400,
                                      "message": "월 저축액은 0보다 커야 합니다.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "연이율이 음수", value = """
                                    {
                                      "status": 400,
                                      "message": "연이율은 0 이상이어야 합니다.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "연이율이 100% 초과", value = """
                                    {
                                      "status": 400,
                                      "message": "연이율은 100%(1.0) 이하여야 합니다.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "연이율이 NaN/무한대 등 유효하지 않은 값", value = """
                                    {
                                      "status": 400,
                                      "message": "연이율은 0 이상 100%(1.0) 이하의 유한한 값이어야 합니다.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "존재하지 않는 목표", value = """
                                    {
                                      "status": 400,
                                      "message": "존재하지 않는 목표입니다.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "존재하지 않는 유저 정책", value = """
                                    {
                                      "status": 400,
                                      "message": "존재하지 않는 유저 정책입니다.",
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
    @PostMapping
    public ResponseEntity<ApiResponse<SimulationResponse>> runSimulation(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody SimulationRequest request) {
        return ResponseEntity.ok(
            ApiResponse.ok(simulationService.runSimulation(UUID.fromString(userId), request))
        );
    }

    @Operation(
            summary = "내 시뮬레이션 이력 전체 조회",
            description = "사용자가 지금까지 실행한 모든 시뮬레이션 결과를 조회합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "시뮬레이션 이력 조회 성공",
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
    public ResponseEntity<ApiResponse<List<SimulationResponse>>> getSimulations(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(
            ApiResponse.ok(simulationService.getSimulations(UUID.fromString(userId)))
        );
    }

    @Operation(
            summary = "특정 목표의 시뮬레이션 이력 조회",
            description = "지정한 목표(goalId)에 대해 실행한 시뮬레이션 이력만 조회합니다. "
                    + "goalId가 존재하지 않거나 본인 소유가 아니어도 에러 없이 빈 배열이 반환됩니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "시뮬레이션 이력 조회 성공",
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
    @GetMapping("/goal/{goalId}")
    public ResponseEntity<ApiResponse<List<SimulationResponse>>> getSimulationsByGoal(
            @AuthenticationPrincipal String userId,
            @Parameter(description = "조회할 목표 ID (UUID)", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID goalId) {
        return ResponseEntity.ok(
            ApiResponse.ok(simulationService.getSimulationsByGoal(UUID.fromString(userId), goalId))
        );
    }
}
