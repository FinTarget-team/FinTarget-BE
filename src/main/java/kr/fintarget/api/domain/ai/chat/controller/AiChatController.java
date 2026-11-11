package kr.fintarget.api.domain.ai.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.ai.chat.dto.AiChatRequest;
import kr.fintarget.api.domain.ai.chat.dto.AiChatResponse;
import kr.fintarget.api.domain.ai.chat.service.AiChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "정책 AI 질문", description = "정책 1건의 저장된 원문을 근거로 AI에게 질문하는 API (#125)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    @Operation(
            summary = "정책 AI 질문",
            description = "정책 상세 화면에서 해당 정책에 대해 질문합니다. 정책의 저장된 원문과 사용자 정보(나이 구간, 소득 구간, 거주 지역, "
                    + "주택 보유 여부, 취업 상태)를 근거로 답하며, 원문에 없는 내용은 답하지 않습니다. "
                    + "conversationId를 생략하면 새 대화를 시작하고, 응답의 conversationId를 넣으면 이어서 질문합니다. "
                    + "한 대화에서는 질문을 " + AiChatService.MAX_TURNS + "개까지 할 수 있고, 마지막 질문 후 24시간이 지나면 대화가 만료됩니다. "
                    + "응답까지 수 초 이상 걸릴 수 있습니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "답변 성공",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "질문이 비었거나 " + AiChatService.MAX_QUESTION_LENGTH + "자를 넘음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(value = """
                            {
                              "status": 400,
                              "message": "질문은 500자 이하로 입력해주세요.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "정책이 없음, 또는 대화가 없거나 만료됨(다른 사용자의 대화, 다른 정책의 대화 포함)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(value = """
                            {
                              "status": 404,
                              "message": "대화를 찾을 수 없습니다. 새 대화를 시작해주세요.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "대화의 질문 수 상한 도달, 또는 같은 대화에 동시에 질문을 보냄",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "AI 응답을 가져오지 못함",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
                    description = "AI 기능을 지금 사용할 수 없음 (비활성, 호출 한도 등)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
    @PostMapping("/policies/{policyId}/ai-chat")
    public ResponseEntity<ApiResponse<AiChatResponse>> ask(
            @AuthenticationPrincipal String userId,
            @Parameter(description = "질문할 정책 ID (UUID)", required = true) @PathVariable UUID policyId,
            @Valid @RequestBody AiChatRequest request) {
        AiChatResponse response = aiChatService.ask(UUID.fromString(userId), policyId,
                request.question().strip(), request.conversationId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "정책 AI 대화 종료", description = "대화와 질문·답변 기록을 삭제합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공. data는 항상 null",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 토큰이 없거나 유효하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "대화가 없거나 다른 사용자의 대화",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
    @DeleteMapping("/ai-chat/{conversationId}")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(
            @AuthenticationPrincipal String userId,
            @Parameter(description = "종료할 대화 ID (UUID)", required = true) @PathVariable UUID conversationId) {
        aiChatService.deleteConversation(UUID.fromString(userId), conversationId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
