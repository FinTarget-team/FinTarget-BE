package kr.fintarget.api.domain.policy.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YouthCenterPaging(
        Integer totCount,
        Integer pageNum,
        Integer pageSize
) {
}
