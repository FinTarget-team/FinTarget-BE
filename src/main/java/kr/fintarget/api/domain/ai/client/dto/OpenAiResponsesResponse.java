package kr.fintarget.api.domain.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * POST /v1/responses 응답 중 사용하는 필드만 받는다.
 * 답변 텍스트는 output[] 중 type=message 항목의 content[] 중 type=output_text의 text에 있다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiResponsesResponse(
        String status,
        String model,
        List<OutputItem> output,
        Usage usage,
        @JsonProperty("incomplete_details") IncompleteDetails incompleteDetails
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutputItem(String type, List<OutputContent> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutputContent(String type, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("output_tokens") Integer outputTokens,
            @JsonProperty("output_tokens_details") OutputTokensDetails outputTokensDetails
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutputTokensDetails(@JsonProperty("reasoning_tokens") Integer reasoningTokens) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IncompleteDetails(String reason) {
    }
}
