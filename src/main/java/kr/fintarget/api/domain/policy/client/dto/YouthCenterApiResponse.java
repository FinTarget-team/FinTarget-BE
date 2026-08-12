package kr.fintarget.api.domain.policy.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YouthCenterApiResponse(
        Integer resultCode,
        String resultMessage,
        YouthCenterResult result
) {
}
