package kr.fintarget.api.domain.policy.mapper;

import kr.fintarget.api.domain.policy.client.dto.KinfaItem;
import kr.fintarget.api.domain.policy.client.dto.YouthCenterPolicyItem;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정책 원문 필드가 PolicyText 컬럼으로 매핑되는지 검증한다 (#113).
 */
class PolicyTextMappingTest {

    // ---------- 온통청년 ----------

    private YouthCenterPolicyItem youthCenterItem(String explnCn, String sprtCn, String aplyYmd, String refUrl, String aplyUrl,
                                                  String addQlfc, String ptcpPrp, String aplyMthd, String srngMthd,
                                                  String sbmsnDcmnt, String etcMttr, String bizBgng, String bizEnd,
                                                  String bizEtc, String earnEtc) {
        return new YouthCenterPolicyItem(
                "YC-1", "정책명", null, explnCn, "주거", null, sprtCn, null, null,
                "19", "39", "N", aplyYmd, refUrl, aplyUrl, null, null, null, null,
                addQlfc, ptcpPrp, aplyMthd, srngMthd, sbmsnDcmnt, etcMttr, bizBgng, bizEnd, bizEtc, earnEtc
        );
    }

    @Test
    void 온통청년_원문_필드가_각_컬럼으로_정제되어_매핑된다() {
        Policy policy = YouthCenterPolicyMapper.toPolicy(youthCenterItem(
                "청년 주거 지원", " 월 20만원 지원\r\n최대 12개월 ", "20260101 ~ 20261231",
                "https://ref.example.com", "https://apply.example.com",
                "무주택자", "타 사업 수혜자", "온라인 신청", "서류 심사", "신청서, 등본", "예산 소진 시 마감",
                "20260101", "20261231", "상시", "중위소득 150% 이하"));

        PolicyText text = policy.getText();
        assertThat(text.getSupportContent()).isEqualTo("월 20만원 지원\n최대 12개월");
        assertThat(text.getEligibilityDetail()).isEqualTo("무주택자");
        assertThat(text.getExclusionCriteria()).isEqualTo("타 사업 수혜자");
        assertThat(text.getIncomeCondition()).isEqualTo("중위소득 150% 이하");
        assertThat(text.getAgeCondition()).isNull();
        assertThat(text.getApplyMethod()).isEqualTo("온라인 신청");
        assertThat(text.getScreeningMethod()).isEqualTo("서류 심사");
        assertThat(text.getRequiredDocuments()).isEqualTo("신청서, 등본");
        assertThat(text.getApplyPeriod()).isEqualTo("20260101 ~ 20261231");
        assertThat(text.getBusinessPeriod()).isEqualTo("20260101 ~ 20261231\n상시");
        assertThat(text.getReferenceUrl()).isEqualTo("https://ref.example.com");
        assertThat(text.getApplyUrl()).isEqualTo("https://apply.example.com");
        assertThat(text.getContactInfo()).isNull();
        assertThat(text.getEtcNote()).isEqualTo("예산 소진 시 마감");
    }

    @Test
    void 온통청년_원문이_비어_있거나_하이픈이면_null이고_해당_없음은_유지된다() {
        Policy policy = YouthCenterPolicyMapper.toPolicy(youthCenterItem(
                "설명", "", "  ", null, "-",
                "해당 없음", null, "-", "", null, "  ",
                null, null, null, null));

        PolicyText text = policy.getText();
        assertThat(text.getSupportContent()).isNull();
        assertThat(text.getApplyPeriod()).isNull();
        assertThat(text.getReferenceUrl()).isNull();
        assertThat(text.getApplyUrl()).isNull();
        assertThat(text.getEligibilityDetail()).isEqualTo("해당 없음");
        assertThat(text.getApplyMethod()).isNull();
        assertThat(text.getScreeningMethod()).isNull();
        assertThat(text.getEtcNote()).isNull();
        assertThat(text.getBusinessPeriod()).isNull();
    }

    @Test
    void 사업_기간이_공백_8칸이면_null이고_기타_문구만_있으면_문구만_저장한다() {
        assertThat(YouthCenterPolicyMapper.toPolicy(youthCenterItem(null, null, null, null, null, null, null, null, null,
                null, null, "        ", "        ", "", null)).getText().getBusinessPeriod()).isNull();
        assertThat(YouthCenterPolicyMapper.toPolicy(youthCenterItem(null, null, null, null, null, null, null, null, null,
                null, null, "        ", "        ", "12월 중 개소 예정", null)).getText().getBusinessPeriod())
                .isEqualTo("12월 중 개소 예정");
    }

    @Test
    void 사업_기간의_한쪽_날짜만_있으면_한쪽만_표시한다() {
        assertThat(YouthCenterPolicyMapper.toPolicy(youthCenterItem(null, null, null, null, null, null, null, null, null,
                null, null, "20260101", "        ", null, null)).getText().getBusinessPeriod()).isEqualTo("20260101 ~");
        assertThat(YouthCenterPolicyMapper.toPolicy(youthCenterItem(null, null, null, null, null, null, null, null, null,
                null, null, null, "20261231", null, null)).getText().getBusinessPeriod()).isEqualTo("~ 20261231");
    }

    @Test
    void 온통청년_description은_HTML_엔티티만_디코딩하고_공백은_그대로_둔다() {
        Policy policy = YouthCenterPolicyMapper.toPolicy(youthCenterItem(
                " 청년&#40;19~39세&#41; 지원\n", null, null, null, null, null, null, null, null, null, null,
                null, null, null, null));

        assertThat(policy.getDescription()).isEqualTo(" 청년(19~39세) 지원\n");
    }

    // ---------- KINFA ----------

    private KinfaItem kinfaItem(String lnLmt, String irtCtg, String irt, String maxTotLnTrm, String maxDfrmTrm,
                                String maxRdptTrm, String rdptMthd, String usge, String trgt, String suprTgtDtlCond,
                                String age, String incm, String jnMthd, String crdtSc, String cnpl, String rltSite,
                                String etcRefSbjc, String mgmDln) {
        return new KinfaItem(
                "202609", "1", "햇살론유스", lnLmt, irtCtg, irt, maxTotLnTrm, maxDfrmTrm, maxRdptTrm, rdptMthd,
                usge, trgt, "서민금융진흥원", "서민금융진흥원", "전국", suprTgtDtlCond, age, incm, jnMthd,
                "Y", "1", "대출상품", crdtSc, cnpl, rltSite, etcRefSbjc, mgmDln
        );
    }

    @Test
    void KINFA_원문은_라벨_줄과_단일_컬럼으로_매핑된다() {
        Policy policy = KinfaPolicyMapper.toPolicy(kinfaItem(
                "1200만원", "고정금리", "연 3.6%", "15년", "6년", "9년", "원리금균등분할상환", "생계",
                "청년", "만 19~34세 미취업 청년", "만 19~34세", "연 3,500만원 이하", "서민금융진흥원 앱",
                "개인신용평점 하위 20%", "1397", "서민금융진흥원 홈페이지", "대학생 포함", "상시"));

        PolicyText text = policy.getText();
        assertThat(text.getSupportContent()).isEqualTo(String.join("\n",
                "대출 한도: 1200만원",
                "금리 구분: 고정금리",
                "금리: 연 3.6%",
                "최대 총 대출 기간: 15년",
                "최대 거치 기간: 6년",
                "최대 상환 기간: 9년",
                "상환 방법: 원리금균등분할상환",
                "자금 용도: 생계"));
        assertThat(text.getEligibilityDetail()).isEqualTo("대상: 청년\n신용 조건: 개인신용평점 하위 20%");
        assertThat(text.getIncomeCondition()).isEqualTo("연 3,500만원 이하");
        assertThat(text.getAgeCondition()).isEqualTo("만 19~34세");
        assertThat(text.getApplyMethod()).isEqualTo("서민금융진흥원 앱");
        assertThat(text.getApplyPeriod()).isEqualTo("상시");
        assertThat(text.getContactInfo()).isEqualTo("문의처: 1397\n관련 사이트: 서민금융진흥원 홈페이지");
        assertThat(text.getEtcNote()).isEqualTo("대학생 포함");
        assertThat(text.getExclusionCriteria()).isNull();
        assertThat(text.getScreeningMethod()).isNull();
        assertThat(text.getRequiredDocuments()).isNull();
        assertThat(text.getBusinessPeriod()).isNull();
        assertThat(text.getReferenceUrl()).isNull();
        assertThat(text.getApplyUrl()).isNull();
    }

    @Test
    void KINFA_라벨_줄은_값이_빈_필드를_줄째로_빼고_모두_비면_null이다() {
        Policy policy = KinfaPolicyMapper.toPolicy(kinfaItem(
                "3500만원", null, "", "-", "-", "-", "원(리)금균등분할상환", null,
                null, "조건", "없음", "없음", null, "  ", null, "-", null, null));

        PolicyText text = policy.getText();
        assertThat(text.getSupportContent()).isEqualTo("대출 한도: 3500만원\n상환 방법: 원(리)금균등분할상환");
        assertThat(text.getEligibilityDetail()).isNull();
        assertThat(text.getContactInfo()).isNull();
        assertThat(text.getIncomeCondition()).isEqualTo("없음");
        assertThat(text.getAgeCondition()).isEqualTo("없음");
        assertThat(text.getEtcNote()).isNull();
    }

    @Test
    void KINFA_description은_자격_조건이며_엔티티만_디코딩되고_eligibility에_중복되지_않는다() {
        Policy policy = KinfaPolicyMapper.toPolicy(kinfaItem(
                null, null, null, null, null, null, null, null,
                "자영업자", "근로장려금 신청 대상&#40;수급자&#41;인 자영업자", null, null, null,
                null, null, null, null, null));

        assertThat(policy.getDescription()).isEqualTo("근로장려금 신청 대상(수급자)인 자영업자");
        assertThat(policy.getText().getEligibilityDetail()).isEqualTo("대상: 자영업자");
    }

    @Test
    void KINFA_원문의_HTML_엔티티는_디코딩된다() {
        Policy policy = KinfaPolicyMapper.toPolicy(kinfaItem(
                null, null, null, null, null, null, "원&#40;리&#41;금균등", null,
                null, null, null, null, "영업점 방문&#40;예약&#41;", null, null, null, null, null));

        assertThat(policy.getText().getSupportContent()).isEqualTo("상환 방법: 원(리)금균등");
        assertThat(policy.getText().getApplyMethod()).isEqualTo("영업점 방문(예약)");
    }
}
