package kr.fintarget.api.domain.policy.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.fintarget.api.domain.policy.client.dto.YouthCenterApiResponse;
import kr.fintarget.api.domain.policy.client.dto.YouthCenterPolicyItem;
import kr.fintarget.api.domain.policy.entity.Policy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2026-08-11 온통청년 API 실호출로 확보한 실제 응답 샘플(JSON)을 그대로 사용해
 * DTO 역직렬화와 Policy 매핑이 정상 동작하는지 검증한다.
 */
class YouthCenterPolicyMapperTest {

    private static final String REAL_SAMPLE_RESPONSE = """
            {
                "resultCode": 200,
                "resultMessage": "성공적으로 데이터를 가지고 왔습니다.",
                "result": {
                    "pagging": {
                        "totCount": 2646,
                        "pageNum": 1,
                        "pageSize": 10
                    },
                    "youthPolicyList": [
                        {
                            "plcyNo": "20260718005400113263",
                            "bscPlanCycl": "2",
                            "bscPlanPlcyWayNo": "002",
                            "bscPlanFcsAsmtNo": "005",
                            "bscPlanAsmtNo": "019",
                            "pvsnInstGroupCd": "0054001",
                            "plcyPvsnMthdCd": "0042002",
                            "plcyAprvSttsCd": "0044002",
                            "plcyNm": "K-뉴딜아카데미",
                            "plcyKywdNm": "교육지원",
                            "plcyExplnCn": "청년이 선호하는 대기업 등이 직무훈련 및 자율기획훈련을 설계·제공하여 청년의 자신감 회복 및 재도약을 지원\\n",
                            "lclsfNm": "교육･직업훈련",
                            "mclsfNm": "미래역량강화",
                            "plcySprtCn": "청년(15~34세) 미취업자 대상 훈련비 및 참여수당 지원",
                            "sprvsnInstCd": "1492000",
                            "sprvsnInstCdNm": "고용노동부",
                            "sprtSclLmtYn": "N",
                            "aplyPrdSeCd": "0057002",
                            "bizPrdSeCd": "0056002",
                            "bizPrdEtcCn": "상시",
                            "refUrlAddr1": "https://www.work24.go.kr",
                            "sprtTrgtMinAge": "15",
                            "sprtTrgtMaxAge": "34",
                            "sprtTrgtAgeLmtYn": "N",
                            "mrgSttsCd": "0055003",
                            "earnCndSeCd": "0043001",
                            "aplyYmd": "",
                            "frstRegDt": "2026-07-18 14:21:14",
                            "lastMdfcnDt": "2026-07-20 09:29:41",
                            "sbizCd": "0014010"
                        },
                        {
                            "plcyNo": "20260718005400113262",
                            "plcyNm": "경계선지능청년지원",
                            "plcyKywdNm": "교육지원",
                            "plcyExplnCn": "법상 지적장애에 해당하지 않아 각종 지원 사각지대에 존재하는 경계선 지능 청년(18~39세)에게 상담, 기초소양 및 구직기술 습득 지원 등 맞춤형 프로그램 지원\\n",
                            "lclsfNm": "금융･복지･문화",
                            "mclsfNm": "취약계층 및 금융지원",
                            "plcySprtCn": "참여자 참여수당(1인 20만원) 및 지자체 사업비(1인 80만원) 지급",
                            "sprvsnInstCdNm": "고용노동부",
                            "sprtTrgtMinAge": "18",
                            "sprtTrgtMaxAge": "39",
                            "aplyYmd": "20260101 ~ 20261231",
                            "refUrlAddr1": "https://www.moel.go.kr/policyitrd/policyItrdView.do?policy_itrd_sn=413"
                        },
                        {
                            "plcyNo": "20260710005400213254",
                            "plcyNm": "2026년 신혼부부 전세자금 대출이자 지원 안내",
                            "plcyKywdNm": "주거지원",
                            "plcyExplnCn": "주택도시기금 신혼부부 전용 전세자금대출 신규 대출자 및 대출 연장자 대출이자 지원",
                            "lclsfNm": "주거",
                            "mclsfNm": "전월세 및 주거급여 지원",
                            "plcySprtCn": "납입 대출이자의 일부 지원 (무자녀 0.5%, 1자녀 0.7%, 2자녀이상 1.0%)",
                            "sprvsnInstCdNm": "전남광주통합특별시",
                            "sprtTrgtMinAge": "1",
                            "sprtTrgtMaxAge": "99",
                            "aplyYmd": "20260105 ~ 20261030",
                            "aplyUrlAddr": "https://www.xn--hc0by27bu6atul3dc6t.kr/main/rentSubsidy"
                        }
                    ]
                }
            }
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 실제_응답_샘플을_DTO로_역직렬화한다() throws Exception {
        YouthCenterApiResponse response = objectMapper.readValue(REAL_SAMPLE_RESPONSE, YouthCenterApiResponse.class);

        assertThat(response.resultCode()).isEqualTo(200);
        assertThat(response.result().pagging().totCount()).isEqualTo(2646);
        assertThat(response.result().youthPolicyList()).hasSize(3);
    }

    @Test
    void 정책대분류가_교육인_항목은_필터링_대상에서_제외한다() throws Exception {
        List<YouthCenterPolicyItem> items = parseItems();

        assertThat(items.get(0).lclsfNm()).isEqualTo("교육･직업훈련");
    }

    @Test
    void 금융복지_정책이_Policy_엔티티로_정상_매핑된다() throws Exception {
        YouthCenterPolicyItem item = parseItems().get(1);

        Policy policy = YouthCenterPolicyMapper.toPolicy(item);

        assertThat(policy.getName()).isEqualTo("경계선지능청년지원");
        assertThat(policy.getDescription()).contains("경계선 지능 청년");
        assertThat(policy.getMinAge()).isEqualTo(18);
        assertThat(policy.getMaxAge()).isEqualTo(39);
        assertThat(policy.getPolicyType()).isEqualTo("금융･복지･문화");
        assertThat(policy.getRegion()).isEqualTo("고용노동부");
        assertThat(policy.getExternalId()).isEqualTo("20260718005400113262");
        assertThat(policy.getSource()).isEqualTo("YOUTH_CENTER");
    }

    @Test
    void 주거_정책이_Policy_엔티티로_정상_매핑된다() throws Exception {
        YouthCenterPolicyItem item = parseItems().get(2);

        Policy policy = YouthCenterPolicyMapper.toPolicy(item);

        assertThat(policy.getName()).isEqualTo("2026년 신혼부부 전세자금 대출이자 지원 안내");
        assertThat(policy.getMinAge()).isEqualTo(1);
        assertThat(policy.getMaxAge()).isEqualTo(99);
        assertThat(policy.getPolicyType()).isEqualTo("주거");
        assertThat(policy.getRegion()).isEqualTo("전남광주통합특별시");
        assertThat(policy.getExternalId()).isEqualTo("20260710005400213254");
        assertThat(policy.getSource()).isEqualTo("YOUTH_CENTER");
        assertThat(policy.getIncomeLimit()).isNull();
        assertThat(policy.getBenefitAmount()).isNull();
    }

    private List<YouthCenterPolicyItem> parseItems() throws Exception {
        return objectMapper.readValue(REAL_SAMPLE_RESPONSE, YouthCenterApiResponse.class)
                .result()
                .youthPolicyList();
    }
}
