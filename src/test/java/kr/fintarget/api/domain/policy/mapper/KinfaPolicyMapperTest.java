package kr.fintarget.api.domain.policy.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.fintarget.api.domain.policy.client.dto.KinfaApiResponse;
import kr.fintarget.api.domain.policy.client.dto.KinfaItem;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2026-08-11 KINFA(서민금융상품기본정보) API 실호출로 확보한 실제 응답 샘플(JSON)을 그대로 사용해
 * DTO 역직렬화와 Policy 매핑이 정상 동작하는지 검증한다.
 */
class KinfaPolicyMapperTest {

    private static final String REAL_SAMPLE_RESPONSE = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"numOfRows":3,"pageNo":1,"totalCount":10145,"items":{"item":[{"basYm":"202607","snq":"1","finPrdNm":"새희망홀씨Ⅱ","lnLmt":"3500만원","irtCtg":"변동금리","irt":"은행별 상이(10.5이하)","maxTotLnTrm":"은행별 상이","maxDfrmTrm":"-","maxRdptTrm":"-","rdptMthd":"원(리)금균등분할상환","usge":"생계","trgt":"근로자","instCtg":"시중은행","ofrInstNm":"14개 취급은행","rsdAreaPamtEqltIstm":"전국","suprTgtDtlCond":"연소득 4천만원 이하 또는 연소득 5천만원 이하(신용평점 하위20%이하)인 자","age":"없음","incm":"없음","jnMthd":"14개 취급은행 신청방법 문의","prdExisYn":"Y","prdCtg":"1","prdNm":"대출상품"}]}}}}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 실제_응답_샘플을_DTO로_역직렬화한다() throws Exception {
        KinfaApiResponse response = objectMapper.readValue(REAL_SAMPLE_RESPONSE, KinfaApiResponse.class);

        assertThat(response.response().header().resultCode()).isEqualTo("00");
        assertThat(response.response().body().totalCount()).isEqualTo(10145);
        assertThat(response.response().body().items().item()).hasSize(1);
    }

    @Test
    void 서민금융상품이_Policy_엔티티로_정상_매핑된다() throws Exception {
        KinfaItem item = parseItem();

        Policy policy = KinfaPolicyMapper.toPolicy(item);

        assertThat(policy.getName()).isEqualTo("새희망홀씨Ⅱ");
        assertThat(policy.getDescription()).contains("연소득 4천만원 이하");
        assertThat(policy.getMinAge()).isNull();
        assertThat(policy.getMaxAge()).isNull();
        assertThat(policy.getIncomeLimit()).isNull();
        assertThat(policy.getBenefitAmount()).isEqualTo(35_000_000L);
        assertThat(policy.getPolicyType()).isEqualTo(PolicyType.FINANCE_WELFARE);
        assertThat(policy.getRegion()).isNull();
        assertThat(policy.getExternalId()).isEqualTo("14개 취급은행|새희망홀씨Ⅱ");
        assertThat(policy.getSource()).isEqualTo("KINFA");
    }

    @Test
    void usge에_창업이_포함되면_STARTUP으로_매핑된다() throws Exception {
        KinfaItem realItem = parseItem();
        KinfaItem startupItem = new KinfaItem(
                realItem.basYm(), realItem.snq(), realItem.finPrdNm(), realItem.lnLmt(),
                realItem.irtCtg(), realItem.irt(), realItem.maxTotLnTrm(), realItem.maxDfrmTrm(),
                realItem.maxRdptTrm(), realItem.rdptMthd(), "청년창업", realItem.trgt(),
                realItem.instCtg(), realItem.ofrInstNm(), realItem.rsdAreaPamtEqltIstm(),
                realItem.suprTgtDtlCond(), realItem.age(), realItem.incm(), realItem.jnMthd(),
                realItem.prdExisYn(), realItem.prdCtg(), realItem.prdNm()
        );

        Policy policy = KinfaPolicyMapper.toPolicy(startupItem);

        assertThat(policy.getPolicyType()).isEqualTo(PolicyType.STARTUP);
    }

    @Test
    void 제공기관명과_상품명을_조합해_externalId를_만든다() throws Exception {
        KinfaItem item = parseItem();

        // 기준연월(basYm)/순번(snq)은 달마다 바뀌므로 식별자에 포함하지 않는다
        assertThat(KinfaPolicyMapper.toExternalId(item)).isEqualTo("14개 취급은행|새희망홀씨Ⅱ");
    }

    private KinfaItem parseItem() throws Exception {
        List<KinfaItem> items = objectMapper.readValue(REAL_SAMPLE_RESPONSE, KinfaApiResponse.class)
                .response().body().items().item();
        return items.get(0);
    }

    private KinfaItem regionItem(String rsdAreaPamtEqltIstm, String finPrdNm) {
        return new KinfaItem(
                null, null, finPrdNm, null,
                null, null, null, null,
                null, null, null, null,
                null, "기관", rsdAreaPamtEqltIstm,
                null, null, null, null,
                null, null, null
        );
    }

    @Test
    void 지역_약칭은_Region_코드로_변환된다() {
        assertThat(KinfaPolicyMapper.toPolicy(regionItem("서울", "상품")).getRegion()).isEqualTo("SEOUL");
        assertThat(KinfaPolicyMapper.toPolicy(regionItem("전북", "상품")).getRegion()).isEqualTo("JEONBUK");
    }

    @Test
    void 전국_관련_값은_null로_변환된다() {
        assertThat(KinfaPolicyMapper.toPolicy(regionItem("전국", "상품")).getRegion()).isNull();
        assertThat(KinfaPolicyMapper.toPolicy(regionItem("전국(농어촌)", "상품")).getRegion()).isNull();
    }

    @Test
    void 쉼표로_구분된_다중_지역은_각각_코드로_변환되어_쉼표로_연결된다() {
        assertThat(KinfaPolicyMapper.toPolicy(regionItem("경남, 울산", "상품")).getRegion())
                .isEqualTo("GYEONGNAM,ULSAN");
    }

    @Test
    void 지역값이_하이픈이면_정책명의_시도_정식명칭으로_보정된다() {
        assertThat(KinfaPolicyMapper.toPolicy(
                regionItem("-", "경기도 중소기업 육성자금(특별경영자금_재해피해)")).getRegion())
                .isEqualTo("GYEONGGI");
    }

    @Test
    void 지역값이_하이픈이고_정책명에_정식명칭이_없으면_null이다() {
        assertThat(KinfaPolicyMapper.toPolicy(regionItem("-", "햇살론일반")).getRegion()).isNull();
        assertThat(KinfaPolicyMapper.toPolicy(regionItem("-", "성실상환자 대출(소액금융)")).getRegion()).isNull();
    }

    @Test
    void 지역값이_하이픈이고_정책명에_약칭만_있으면_오탐_없이_null이다() {
        assertThat(KinfaPolicyMapper.toPolicy(regionItem("-", "서울 청년 대출")).getRegion()).isNull();
        assertThat(KinfaPolicyMapper.toPolicy(regionItem("-", "경기침체 극복 자금")).getRegion()).isNull();
    }

    @Test
    void 지역값이_null이거나_빈_문자열이면_null이다() {
        assertThat(KinfaPolicyMapper.toPolicy(regionItem(null, "상품")).getRegion()).isNull();
        assertThat(KinfaPolicyMapper.toPolicy(regionItem("", "상품")).getRegion()).isNull();
    }

    @Test
    void 알_수_없는_지역값은_null이다() {
        assertThat(KinfaPolicyMapper.toPolicy(regionItem("미지정", "상품")).getRegion()).isNull();
    }
}
