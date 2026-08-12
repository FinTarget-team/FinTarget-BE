package kr.fintarget.api.domain.policy.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KinfaBody(
        Integer numOfRows,
        Integer pageNo,
        Integer totalCount,
        KinfaItems items
) {
}
