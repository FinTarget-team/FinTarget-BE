package kr.fintarget.api.domain.policy.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KinfaApiResponse(
        KinfaResponse response
) {
}
