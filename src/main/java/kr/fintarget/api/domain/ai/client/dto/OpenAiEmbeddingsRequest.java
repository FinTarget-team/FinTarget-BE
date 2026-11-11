package kr.fintarget.api.domain.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * POST /v1/embeddings 요청 본문.
 * 상한(API 레퍼런스): input 배열 2048개, 입력 1개당 8192토큰, 요청 전체 300,000토큰.
 */
public record OpenAiEmbeddingsRequest(
        String model,
        List<String> input,
        Integer dimensions,
        @JsonProperty("encoding_format") String encodingFormat
) {
}
