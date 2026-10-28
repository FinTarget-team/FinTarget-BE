package kr.fintarget.api.domain.policy.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.fintarget.api.domain.policy.client.dto.YouthCenterApiResponse;
import kr.fintarget.api.domain.policy.client.dto.YouthCenterPolicyItem;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

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
                            "refUrlAddr1": "https://www.moel.go.kr/policyitrd/policyItrdView.do?policy_itrd_sn=413",
                            "zipCd": "41135"
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
                            "aplyUrlAddr": "https://www.xn--hc0by27bu6atul3dc6t.kr/main/rentSubsidy",
                            "zipCd": "29000,46000"
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
    void 주거_금융_외_카테고리는_ETC로_매핑된다() throws Exception {
        YouthCenterPolicyItem item = parseItems().get(0);

        Policy policy = YouthCenterPolicyMapper.toPolicy(item);

        assertThat(policy.getPolicyType()).isEqualTo(PolicyType.ETC);
    }

    @Test
    void 금융복지_정책이_Policy_엔티티로_정상_매핑된다() throws Exception {
        YouthCenterPolicyItem item = parseItems().get(1);

        Policy policy = YouthCenterPolicyMapper.toPolicy(item);

        assertThat(policy.getName()).isEqualTo("경계선지능청년지원");
        assertThat(policy.getDescription()).contains("경계선 지능 청년");
        assertThat(policy.getMinAge()).isEqualTo(18);
        assertThat(policy.getMaxAge()).isEqualTo(39);
        assertThat(policy.getPolicyType()).isEqualTo(PolicyType.FINANCE_WELFARE);
        assertThat(policy.getRegion()).isEqualTo("GYEONGGI");
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
        assertThat(policy.getPolicyType()).isEqualTo(PolicyType.HOUSING);
        assertThat(policy.getRegion()).isEqualTo("GWANGJU,JEONNAM");
        assertThat(policy.getExternalId()).isEqualTo("20260710005400213254");
        assertThat(policy.getSource()).isEqualTo("YOUTH_CENTER");
        assertThat(policy.getIncomeLimit()).isNull();
        assertThat(policy.getBenefitAmount()).isNull();
    }

    @Test
    void 단일_시군구_코드는_해당_시도로_변환된다() {
        assertThat(YouthCenterPolicyMapper.toPolicy(itemWithZipCd("41135")).getRegion())
                .isEqualTo("GYEONGGI");
    }

    @Test
    void 같은_시도의_여러_시군구는_중복_제거되어_하나의_코드로_변환된다() {
        assertThat(YouthCenterPolicyMapper.toPolicy(itemWithZipCd("41135,41111")).getRegion())
                .isEqualTo("GYEONGGI");
    }

    @Test
    void 여러_시도가_섞이면_입력_순서대로_쉼표로_연결된다() {
        assertThat(YouthCenterPolicyMapper.toPolicy(itemWithZipCd("47000,43000")).getRegion())
                .isEqualTo("GYEONGBUK,CHUNGBUK");
    }

    @Test
    void 알려진_시도_prefix_16개_전부를_포함하면_전국으로_판정되어_null이다() {
        String allSido = "11,26,27,28,30,31,36,41,43,44,47,48,50,51,52,12";

        assertThat(YouthCenterPolicyMapper.toPolicy(itemWithZipCd(allSido)).getRegion()).isNull();
    }

    @Test
    void 전국_목록에서_하나라도_빠지면_전국으로_판정되지_않는다() {
        String without43 = "11,26,27,28,30,31,36,41,44,47,48,50,51,52,12"; // 43(충북) 제외
        String without51 = "11,26,27,28,30,31,36,41,43,44,47,48,50,52,12"; // 51(강원) 제외

        assertThat(YouthCenterPolicyMapper.toPolicy(itemWithZipCd(without43)).getRegion()).isNotNull();
        assertThat(YouthCenterPolicyMapper.toPolicy(itemWithZipCd(without51)).getRegion()).isNotNull();
    }

    @Test
    void 구_코드로만_구성된_전국_목록도_전국으로_판정되어_null이다() {
        // 51(강원)/52(전북)/12(전남광주) 대신 구 코드 42(강원)/45(전북)/29(광주)/46(전남)을 사용
        String allSidoWithOldCodes = "11,26,27,28,30,31,36,41,42,43,44,45,47,48,50,29,46";

        assertThat(YouthCenterPolicyMapper.toPolicy(itemWithZipCd(allSidoWithOldCodes)).getRegion()).isNull();
    }

    @Test
    void 구_전남광주_통합_코드_12는_광주와_전남_두_지역으로_전개된다() {
        assertThat(YouthCenterPolicyMapper.toPolicy(itemWithZipCd("12345")).getRegion())
                .isEqualTo("GWANGJU,JEONNAM");
    }

    @Test
    void 구_코드_42_45_29_46도_각각_하나의_시도로_변환된다() {
        assertThat(YouthCenterPolicyMapper.toPolicy(itemWithZipCd("42000")).getRegion()).isEqualTo("GANGWON");
        assertThat(YouthCenterPolicyMapper.toPolicy(itemWithZipCd("45000")).getRegion()).isEqualTo("JEONBUK");
        assertThat(YouthCenterPolicyMapper.toPolicy(itemWithZipCd("29000")).getRegion()).isEqualTo("GWANGJU");
        assertThat(YouthCenterPolicyMapper.toPolicy(itemWithZipCd("46000")).getRegion()).isEqualTo("JEONNAM");
    }

    @Test
    void 알_수_없는_prefix는_제외되고_나머지만_변환된다() {
        assertThat(YouthCenterPolicyMapper.toPolicy(itemWithZipCd("41000,99000")).getRegion())
                .isEqualTo("GYEONGGI");
    }

    @Test
    void 모든_prefix가_알_수_없으면_null이다() {
        assertThat(YouthCenterPolicyMapper.toPolicy(itemWithZipCd("99000,98000")).getRegion()).isNull();
    }

    @Test
    void zipCd가_빈_문자열이거나_null이면_null이다() {
        assertThat(YouthCenterPolicyMapper.toPolicy(itemWithZipCd("")).getRegion()).isNull();
        assertThat(YouthCenterPolicyMapper.toPolicy(itemWithZipCd(null)).getRegion()).isNull();
    }

    @Test
    void 나이가_0이면_최소_최대_모두_제한_없음으로_null이다() {
        Policy policy = YouthCenterPolicyMapper.toPolicy(itemWithAges("0", "0", "Y"));

        assertThat(policy.getMinAge()).isNull();
        assertThat(policy.getMaxAge()).isNull();
    }

    @Test
    void 최대_나이만_0이면_최소_나이는_유지하고_상한은_null이다() {
        Policy policy = YouthCenterPolicyMapper.toPolicy(itemWithAges("18", "0", "Y"));

        assertThat(policy.getMinAge()).isEqualTo(18);
        assertThat(policy.getMaxAge()).isNull();
    }

    @Test
    void 최소_나이만_0이면_하한은_null이고_최대_나이는_유지한다() {
        Policy policy = YouthCenterPolicyMapper.toPolicy(itemWithAges("0", "34", "N"));

        assertThat(policy.getMinAge()).isNull();
        assertThat(policy.getMaxAge()).isEqualTo(34);
    }

    @Test
    void 음수_나이도_제한_없음으로_null이다() {
        Policy policy = YouthCenterPolicyMapper.toPolicy(itemWithAges("-1", "-5", "N"));

        assertThat(policy.getMinAge()).isNull();
        assertThat(policy.getMaxAge()).isNull();
    }

    @Test
    void 빈_값과_공백은_null이고_앞뒤_공백이_있는_0도_null이다() {
        assertThat(YouthCenterPolicyMapper.toPolicy(itemWithAges("", "   ", "Y")).getMinAge()).isNull();
        assertThat(YouthCenterPolicyMapper.toPolicy(itemWithAges("", "   ", "Y")).getMaxAge()).isNull();
        assertThat(YouthCenterPolicyMapper.toPolicy(itemWithAges(" 0 ", null, "Y")).getMinAge()).isNull();
        assertThat(YouthCenterPolicyMapper.toPolicy(itemWithAges(" 0 ", null, "Y")).getMaxAge()).isNull();
    }

    @Test
    void 양수_나이는_그대로_저장되고_99_120처럼_큰_상한도_유지한다() {
        Policy normal = YouthCenterPolicyMapper.toPolicy(itemWithAges(" 19 ", "39", "N"));
        Policy upper99 = YouthCenterPolicyMapper.toPolicy(itemWithAges("34", "99", "Y"));
        Policy upper120 = YouthCenterPolicyMapper.toPolicy(itemWithAges("1", "120", "N"));

        assertThat(normal.getMinAge()).isEqualTo(19);
        assertThat(normal.getMaxAge()).isEqualTo(39);
        assertThat(upper99.getMaxAge()).isEqualTo(99);
        assertThat(upper120.getMinAge()).isEqualTo(1);
        assertThat(upper120.getMaxAge()).isEqualTo(120);
    }

    @Test
    void 나이_제한_여부_플래그와_관계없이_나이_값으로만_판정한다() {
        for (String flag : new String[]{"Y", "N", null, ""}) {
            Policy limited = YouthCenterPolicyMapper.toPolicy(itemWithAges("19", "39", flag));
            Policy unlimited = YouthCenterPolicyMapper.toPolicy(itemWithAges("0", "0", flag));

            assertThat(limited.getMinAge()).isEqualTo(19);
            assertThat(limited.getMaxAge()).isEqualTo(39);
            assertThat(unlimited.getMinAge()).isNull();
            assertThat(unlimited.getMaxAge()).isNull();
        }
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void 숫자가_아닌_나이는_null로_저장하고_정책_ID와_원래_값을_경고_로그로_남긴다(CapturedOutput output) {
        Policy policy = YouthCenterPolicyMapper.toPolicy(itemWithAges("19세", "만 39세", "N"));

        assertThat(policy.getMinAge()).isNull();
        assertThat(policy.getMaxAge()).isNull();
        assertThat(output.getOut())
                .contains("plcyNo=AGE-TEST, sprtTrgtMinAge=19세")
                .contains("plcyNo=AGE-TEST, sprtTrgtMaxAge=만 39세");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void 숫자인_나이와_0은_경고_로그를_남기지_않는다(CapturedOutput output) {
        YouthCenterPolicyMapper.toPolicy(itemWithAges("0", "39", "Y"));

        assertThat(output.getOut()).doesNotContain("plcyNo=AGE-TEST");
    }

    private YouthCenterPolicyItem itemWithAges(String minAge, String maxAge, String ageLmtYn) {
        return new YouthCenterPolicyItem(
                "AGE-TEST", "plcyNm", null, null, null, null, null, null, null,
                minAge, maxAge, ageLmtYn, null, null, null, null, null, null, null
        );
    }

    private YouthCenterPolicyItem itemWithZipCd(String zipCd) {
        return new YouthCenterPolicyItem(
                "plcyNo", "plcyNm", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, zipCd
        );
    }

    private List<YouthCenterPolicyItem> parseItems() throws Exception {
        return objectMapper.readValue(REAL_SAMPLE_RESPONSE, YouthCenterApiResponse.class)
                .result()
                .youthPolicyList();
    }
}
