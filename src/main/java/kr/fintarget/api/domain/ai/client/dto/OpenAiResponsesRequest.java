package kr.fintarget.api.domain.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * POST /v1/responses 요청 본문.
 * store=false: 기본값(true)이면 응답이 OpenAI에 30일 이상 저장되므로 저장하지 않는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiResponsesRequest(
        String model,
        String instructions,
        List<InputMessage> input,
        @JsonProperty("max_output_tokens") Integer maxOutputTokens,
        Reasoning reasoning,
        Boolean store
) {

    public record InputMessage(String role, String content) {
    }

    public record Reasoning(String effort) {
    }
}
