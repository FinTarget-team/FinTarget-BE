package kr.fintarget.api.domain.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * OpenAI 오류 응답. 분기에 필요한 type, code만 받고 message는 받지 않는다 (로그·예외에 남기지 않기 위해).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiErrorResponse(Error error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(String type, String code) {
    }
}
