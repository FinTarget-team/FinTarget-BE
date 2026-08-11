package kr.fintarget.api.domain.policy.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 실제 응답으로 검증된 필드만 매핑한다 (2026-08-11 실호출 샘플 기준).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YouthCenterPolicyItem(
        String plcyNo,
        String plcyNm,
        String plcyKywdNm,
        String plcyExplnCn,
        String lclsfNm,
        String mclsfNm,
        String plcySprtCn,
        String sprvsnInstCd,
        String sprvsnInstCdNm,
        String sprtTrgtMinAge,
        String sprtTrgtMaxAge,
        String sprtTrgtAgeLmtYn,
        String aplyYmd,
        String refUrlAddr1,
        String aplyUrlAddr,
        String frstRegDt,
        String lastMdfcnDt
) {
}
