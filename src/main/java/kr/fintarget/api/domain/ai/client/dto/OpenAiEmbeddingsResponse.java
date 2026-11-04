package kr.fintarget.api.domain.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * POST /v1/embeddings 응답 중 사용하는 필드만 받는다. data[].index가 요청 input의 순서다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiEmbeddingsResponse(
        String model,
        List<Embedding> data,
        Usage usage
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Embedding(Integer index, float[] embedding) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(@JsonProperty("prompt_tokens") Integer promptTokens) {
    }
}
