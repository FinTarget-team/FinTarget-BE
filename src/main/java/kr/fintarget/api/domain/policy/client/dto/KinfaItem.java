package kr.fintarget.api.domain.policy.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 서민금융상품기본정보 API 실제 응답으로 검증된 필드만 매핑한다 (2026-08-11 실호출 샘플 기준).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KinfaItem(
        String basYm,
        String snq,
        String finPrdNm,
        String lnLmt,
        String irtCtg,
        String irt,
        String maxTotLnTrm,
        String maxDfrmTrm,
        String maxRdptTrm,
        String rdptMthd,
        String usge,
        String trgt,
        String instCtg,
        String ofrInstNm,
        String rsdAreaPamtEqltIstm,
        String suprTgtDtlCond,
        String age,
        String incm,
        String jnMthd,
        String prdExisYn,
        String prdCtg,
        String prdNm,
        // 이하 정책 원문 필드 (#113, 2026-10-28 실응답으로 확인). 기존 생성자 호출 순서를 유지하려고 끝에 추가
        String crdtSc,
        String cnpl,
        String rltSite,
        String etcRefSbjc,
        String mgmDln
) {
}
