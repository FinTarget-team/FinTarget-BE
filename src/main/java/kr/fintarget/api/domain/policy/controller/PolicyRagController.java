package kr.fintarget.api.domain.policy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.ai.chat.service.AiChatService;
import kr.fintarget.api.domain.policy.dto.PolicyRagRequest;
import kr.fintarget.api.domain.policy.dto.PolicyRagResponse;
import kr.fintarget.api.domain.policy.rag.PolicyRagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "통합 정책 AI 답변", description = "정책을 고르지 않고 질문하면, 조건에 맞는 정책 원문을 찾아 근거로 답하는 API (#183)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyRagController {

    private final PolicyRagService policyRagService;

    @Operation(
            summary = "통합 정책 AI 답변",
            description = "질문과 의미가 가까운 정책을 정책 검색 API와 같은 방식(나이·소득·지역 조건 → 원문 유사도)으로 찾고, "
                    + "찾은 정책 원문만 근거로 답합니다. 답변과 함께 근거 정책 목록(sources)을 돌려주며, "
                    + "후속 질문은 근거 정책의 단건 정책 AI 질문(POST /api/policies/{policyId}/ai-chat)으로 이어갑니다. "
                    + "이전 질문은 기억하지 않습니다(질문마다 독립). "
                    + "특정 시·군·구 거주자로 한정된 정책은 질문에 그 시·군·구 이름이 있을 때만 찾고, 질문에 대출 의도가 없으면 대출 상품은 제외하되"
                    + " 대출이자·보증료 지원 정책은 찾으며, 질문에 정책 이름이 있으면 그 정책을 먼저 찾습니다. "
                    + "답변은 해요체이고 오늘 날짜로 신청 가능 여부를 판단하며, 특정 대상 전용 정책은 조건으로만 안내합니다(#235). "
                    + "질문과 가장 가까운 정책도 거리가 멀면(구어체·줄임말 질문) 질문을 정책 검색용 문장으로 바꿔 한 번 더 검색해 합칩니다. "
                    + "바꾼 문장은 검색에만 쓰고 답변은 원래 질문으로 만듭니다. 정책 이름이 있거나 가까운 정책이 있는 질문은 바꾸지 않습니다(#241). "
                    + "응답 형식은 바뀌지 않습니다. "
                    + "관련 정책을 찾지 못하면 status=NO_EVIDENCE, 나이와 소득 정보가 모두 없으면 status=PROFILE_INCOMPLETE이며 둘 다 200입니다. "
                    + "사용자별 하루 질문 횟수 제한은 기본 무제한이며, 양수로 설정하면 그 횟수만큼 허용합니다(KST 자정 초기화, #239). "
                    + "AI 호출이 있어 응답까지 수 초 이상 걸릴 수 있습니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "답변 성공. 근거를 찾지 못했거나 조건 정보가 없어도 200이며 status로 구분합니다", useReturnTypeSchema = true),
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
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429",
                    description = "오늘 질문 가능 횟수를 모두 사용함 (AI 호출이 실패한 요청은 횟수에서 빠짐)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(value = """
                            {
                              "status": 429,
                              "message": "오늘 질문할 수 있는 횟수를 모두 사용했습니다. 내일 다시 시도해주세요.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502",
                    description = "AI 응답을 받지 못함 (OpenAI 오류·타임아웃·출력 상한 초과)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(value = """
                            {
                              "status": 502,
                              "message": "AI 응답을 받지 못했습니다. 잠시 후 다시 시도해주세요.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
                    description = "AI 기능 비활성(키 없음) 또는 OpenAI 호출 한도·잔액 초과",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(value = """
                            {
                              "status": 503,
                              "message": "AI 기능을 지금 사용할 수 없습니다. 잠시 후 다시 시도해주세요.",
                              "data": null
                            }
                            """)))
    })
    @PostMapping("/ai-answer")
    public ResponseEntity<ApiResponse<PolicyRagResponse>> answer(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody PolicyRagRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                PolicyRagResponse.from(policyRagService.answer(userId, request.question().strip()))));
    }
}
