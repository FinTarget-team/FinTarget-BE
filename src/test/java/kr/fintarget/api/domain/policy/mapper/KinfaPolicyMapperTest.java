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
        assertThat(policy.getRegion()).isEqualTo("전국");
        assertThat(policy.getExternalId()).isEqualTo("202607-1");
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
    void basYm과_snq를_조합해_externalId를_만든다() throws Exception {
        KinfaItem item = parseItem();

        assertThat(KinfaPolicyMapper.toExternalId(item)).isEqualTo("202607-1");
    }

    private KinfaItem parseItem() throws Exception {
        List<KinfaItem> items = objectMapper.readValue(REAL_SAMPLE_RESPONSE, KinfaApiResponse.class)
                .response().body().items().item();
        return items.get(0);
    }
}
