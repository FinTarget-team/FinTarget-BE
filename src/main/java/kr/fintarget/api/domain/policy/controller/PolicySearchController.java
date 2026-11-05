package kr.fintarget.api.domain.policy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.ai.chat.service.AiChatService;
import kr.fintarget.api.domain.policy.dto.PolicySearchRequest;
import kr.fintarget.api.domain.policy.dto.PolicySearchResponse;
import kr.fintarget.api.domain.policy.search.PolicySearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "정책 검색", description = "자연어 질문으로 사용자 조건에 맞는 정책을 찾는 API (#182)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicySearchController {

    private final PolicySearchService policySearchService;

    @Operation(
            summary = "자연어 질문 기반 정책 검색",
            description = "질문과 의미가 가까운 정책을 찾습니다. 먼저 정책 목록 API와 같은 조건(나이·소득·지역)으로 "
                    + "신청 가능한 정책을 고르고, 그 안에서 저장된 원문과 질문의 유사도로 순위를 매깁니다. "
                    + "답변을 생성하지 않고 정책 목록만 돌려줍니다. "
                    + "나이나 소득 정보가 없는 사용자와, 질문과 가까운 정책이 없는 경우에는 results가 빈 배열입니다(오류가 아닙니다). "
                    + "사용자별 하루 검색 횟수 제한이 있습니다(기본 50회, KST 자정 초기화). 후보 정책이 없어 AI를 호출하지 않은 요청과 AI 호출이 실패한 요청은 횟수에서 빠집니다. "
                    + "질문을 임베딩하는 AI 호출이 있어 응답까지 1초 이상 걸릴 수 있습니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "검색 성공 (맞는 정책이 없으면 results가 빈 배열)", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "질문이 비었거나 " + AiChatService.MAX_QUESTION_LENGTH + "자를 넘음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = {
                            @ExampleObject(name = "질문 누락", value = """
                                    {
                                      "status": 400,
                                      "message": "질문을 입력해주세요.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "질문 길이 초과", value = """
                                    {
                                      "status": 400,
                                      "message": "질문은 500자 이하로 입력해주세요.",
                                      "data": null
                                    }
                                    """)
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429",
                    description = "오늘 검색 가능 횟수를 모두 사용함 (#183)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(value = """
                            {
                              "status": 429,
                              "message": "오늘 검색할 수 있는 횟수를 모두 사용했습니다. 내일 다시 시도해주세요.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502",
                    description = "임베딩 호출 실패 (OpenAI 오류·타임아웃)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(value = """
                            {
                              "status": 502,
                              "message": "AI 응답을 받지 못했습니다. 잠시 후 다시 시도해주세요.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
                    description = "AI 기능 비활성(키 없음) 또는 호출 한도·잔액 초과",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(value = """
                            {
                              "status": 503,
                              "message": "AI 기능을 지금 사용할 수 없습니다. 잠시 후 다시 시도해주세요.",
                              "data": null
                            }
                            """)))
    })
    @PostMapping("/search")
    public ResponseEntity<ApiResponse<PolicySearchResponse>> search(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody PolicySearchRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                PolicySearchResponse.from(policySearchService.search(userId, request.question()))));
    }
}
