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
        String lastMdfcnDt,
        String aplyPrdSeCd,
        String zipCd,
        // 이하 정책 원문 필드 (#113, 2026-10-28 실응답으로 확인). 기존 생성자 호출 순서를 유지하려고 끝에 추가
        String addAplyQlfcCndCn,
        String ptcpPrpTrgtCn,
        String plcyAplyMthdCn,
        String srngMthdCn,
        String sbmsnDcmntCn,
        String etcMttrCn,
        String bizPrdBgngYmd,
        String bizPrdEndYmd,
        String bizPrdEtcCn,
        String earnEtcCn,
        // 이하 소득 조건 필드 (#150, 2026-11-02 실응답과 API 코드정의서로 확인). 기존 생성자 호출 순서를 유지하려고 끝에 추가
        // earnCndSeCd: 0043001 무관, 0043002 연소득, 0043003 기타. 금액 단위는 코드정의서에 없어 실데이터로 만원 단위로 판단
        String earnCndSeCd,
        String earnMinAmt,
        String earnMaxAmt
) {
}
