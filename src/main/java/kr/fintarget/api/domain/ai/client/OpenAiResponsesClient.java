package kr.fintarget.api.domain.ai.client;

import kr.fintarget.api.common.config.OpenAiProperties;
import kr.fintarget.api.domain.ai.client.AiClientException.Reason;
import kr.fintarget.api.domain.ai.client.dto.OpenAiResponsesRequest;
import kr.fintarget.api.domain.ai.client.dto.OpenAiResponsesResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * OpenAI Responses API(POST /v1/responses) 텍스트 생성 클라이언트.
 * 대화 이력은 호출하는 쪽이 매번 전체를 보낸다 (store=false라 previous_response_id를 쓰지 않음).
 * 로그에는 모델명, 토큰 수, 소요 시간만 남긴다.
 */
@Slf4j
@Component
public class OpenAiResponsesClient implements AiTextClient {

    private static final String OPERATION = "responses";
    private static final String PATH = "/responses";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_INCOMPLETE = "incomplete";

    private final OpenAiHttpExecutor executor;

    OpenAiResponsesClient(OpenAiHttpExecutor executor) {
        this.executor = executor;
    }

    @Override
    public AiTextResult generate(AiTextRequest request) {
        OpenAiProperties properties = executor.properties();
        String model = properties.model().idOf(request.tier());
        OpenAiCallResult<OpenAiResponsesResponse> result =
                executor.post(OPERATION, PATH, toRequestBody(request, model, properties), OpenAiResponsesResponse.class);

        OpenAiResponsesResponse response = result.body();
        String responseModel = response.model() == null ? model : response.model();
        int inputTokens = 0;
        int outputTokens = 0;
        int reasoningTokens = 0;
        if (response.usage() != null) {
            inputTokens = orZero(response.usage().inputTokens());
            outputTokens = orZero(response.usage().outputTokens());
            if (response.usage().outputTokensDetails() != null) {
                reasoningTokens = orZero(response.usage().outputTokensDetails().reasoningTokens());
            }
        }

        // 출력 상한에 걸려도 입력·추론 토큰은 과금되므로 토큰 수를 남긴다
        if (STATUS_INCOMPLETE.equals(response.status())) {
            String reason = response.incompleteDetails() == null ? null : response.incompleteDetails().reason();
            log.warn("OpenAI {} 응답 불완전 model={} incompleteReason={} inputTokens={} outputTokens={} reasoningTokens={} elapsedMs={}",
                    OPERATION, responseModel, reason, inputTokens, outputTokens, reasoningTokens, result.elapsedMs());
            throw AiClientException.of(Reason.INCOMPLETE);
        }

        String text = outputText(response);
        if (!STATUS_COMPLETED.equals(response.status()) || text.isBlank()) {
            log.warn("OpenAI {} 응답 이상 model={} status={} textEmpty={} inputTokens={} outputTokens={} elapsedMs={}",
                    OPERATION, responseModel, response.status(), text.isBlank(), inputTokens, outputTokens, result.elapsedMs());
            throw AiClientException.of(Reason.INVALID_RESPONSE);
        }

        log.info("OpenAI {} 호출 완료 model={} inputTokens={} outputTokens={} reasoningTokens={} elapsedMs={} attempts={}",
                OPERATION, responseModel, inputTokens, outputTokens, reasoningTokens, result.elapsedMs(), result.attempts());
        return new AiTextResult(text, responseModel, inputTokens, outputTokens, reasoningTokens);
    }

    private static OpenAiResponsesRequest toRequestBody(AiTextRequest request, String model, OpenAiProperties properties) {
        List<OpenAiResponsesRequest.InputMessage> input = request.messages().stream()
                .map(message -> new OpenAiResponsesRequest.InputMessage(roleOf(message.role()), message.content()))
                .toList();
        return new OpenAiResponsesRequest(
                model,
                request.systemPrompt(),
                input,
                properties.maxOutputTokens(),
                new OpenAiResponsesRequest.Reasoning(properties.reasoningEffort()),
                false);
    }

    private static String roleOf(AiMessage.Role role) {
        return switch (role) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }

    // output_text는 SDK 헬퍼라 HTTP 응답에는 없다. message 항목의 output_text를 순서대로 이어 붙인다
    private static String outputText(OpenAiResponsesResponse response) {
        if (response.output() == null) {
            return "";
        }
        return response.output().stream()
                .filter(item -> "message".equals(item.type()) && item.content() != null)
                .flatMap(item -> item.content().stream())
                .filter(content -> "output_text".equals(content.type()))
                .map(OpenAiResponsesResponse.OutputContent::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining());
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
