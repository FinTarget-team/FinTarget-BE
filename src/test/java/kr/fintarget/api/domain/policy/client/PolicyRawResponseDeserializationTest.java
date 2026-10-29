package kr.fintarget.api.domain.policy.client;

import kr.fintarget.api.domain.policy.client.dto.KinfaItem;
import kr.fintarget.api.domain.policy.client.dto.YouthCenterPolicyItem;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.mapper.KinfaPolicyMapper;
import kr.fintarget.api.domain.policy.mapper.YouthCenterPolicyMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 응답 아이템(src/test/resources/policy, 2026-10-28 수집, 사람 정보는 가짜 값으로 교체)을 실제 API 클라이언트에 흘려보내
 * DTO 역직렬화와 정책 원문(PolicyText) 매핑을 검증한다 (#113).
 * 두 DTO는 ignoreUnknown=true라 필드 이름이 응답 키와 다르면 예외 없이 null이 되므로, 새 필드가 null이 아닌지로 이름을 지킨다.
 * 응답 해석은 WebClientConfig와 같은 기본 코덱(Spring 기본 JSON 디코더, maxInMemorySize 2MB)으로 한다.
 */
class PolicyRawResponseDeserializationTest {

    private static final int MAX_IN_MEMORY_SIZE = 2 * 1024 * 1024;

    private static String resource(String path) throws IOException {
        try (InputStream in = PolicyRawResponseDeserializationTest.class.getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static WebClient stubWebClient(String body) {
        // exchangeFunction을 직접 주면 빌더의 codecs 설정이 응답에 적용되지 않으므로 같은 설정의 strategies를 응답에 준다
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();
        return WebClient.builder()
                .baseUrl("http://stub.local/api")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK, strategies)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(body)
                        .build()))
                .build();
    }

    private static YouthCenterPolicyItem youthCenterItem() throws IOException {
        String body = """
                {"resultCode": 200, "resultMessage": "성공",
                 "result": {"pagging": {"totCount": 1, "pageNum": 1, "pageSize": 100},
                            "youthPolicyList": [%s]}}
                """.formatted(resource("/policy/youthcenter-item.json"));
        YouthCenterApiClient client = new YouthCenterApiClient(stubWebClient(body));
        ReflectionTestUtils.setField(client, "apiKey", "dummy");
        ReflectionTestUtils.setField(client, "pageInterval", Duration.ZERO);

        // 샘플의 신청 기간(20260928 ~ 20261120) 안의 날짜로 조회해야 신청 마감 필터를 통과한다
        List<YouthCenterPolicyItem> items = client.fetchOpenHousingAndFinancePolicies(LocalDate.of(2026, 10, 28));
        assertThat(items).hasSize(1);
        return items.get(0);
    }

    private static KinfaItem kinfaItem() throws IOException {
        String body = """
                {"response": {"header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                              "body": {"numOfRows": 100, "pageNo": 1, "totalCount": 1,
                                       "items": {"item": [%s]}}}}
                """.formatted(resource("/policy/kinfa-item.json"));
        KinfaApiClient client = new KinfaApiClient(stubWebClient(body));
        ReflectionTestUtils.setField(client, "apiKey", "dummy");

        List<KinfaItem> items = client.fetchLatestActiveProducts();
        assertThat(items).hasSize(1);
        return items.get(0);
    }

    @Test
    void 온통청년_실제_응답의_원문_필드_10개가_DTO로_역직렬화된다() throws IOException {
        YouthCenterPolicyItem item = youthCenterItem();

        // 값이 빈 문자열인 필드(bizPrdEtcCn)도 키가 매핑되면 null이 아니다
        assertThat(item.addAplyQlfcCndCn()).isNotNull();
        assertThat(item.ptcpPrpTrgtCn()).isNotNull();
        assertThat(item.plcyAplyMthdCn()).isNotNull();
        assertThat(item.srngMthdCn()).isNotNull();
        assertThat(item.sbmsnDcmntCn()).isNotNull();
        assertThat(item.etcMttrCn()).isNotNull();
        assertThat(item.bizPrdBgngYmd()).isEqualTo("20260928");
        assertThat(item.bizPrdEndYmd()).isEqualTo("20261120");
        assertThat(item.bizPrdEtcCn()).isNotNull();
        assertThat(item.earnEtcCn()).isEqualTo("신청인 본인 연소득 4천만원 이하, 부부일 경우 8천만원 이하");
    }

    @Test
    void 온통청년_실제_응답이_PolicyText_주요_컬럼으로_매핑된다() throws IOException {
        PolicyText text = YouthCenterPolicyMapper.toPolicy(youthCenterItem()).getText();

        assertThat(text.getSupportContent()).isNotBlank();
        assertThat(text.getEligibilityDetail()).startsWith("□ 지원 대상 주택");
        assertThat(text.getExclusionCriteria()).startsWith("□ 참여 제한 대상");
        assertThat(text.getIncomeCondition()).contains("연소득 4천만원 이하");
        assertThat(text.getApplyMethod()).contains("잡아바 어플라이");
        assertThat(text.getScreeningMethod()).isNotBlank();
        assertThat(text.getRequiredDocuments()).startsWith("1. 신청서 1부");
        assertThat(text.getApplyPeriod()).isEqualTo("20260928 ~ 20261120");
        assertThat(text.getBusinessPeriod()).isEqualTo("20260928 ~ 20261120");
        assertThat(text.getReferenceUrl()).startsWith("https://www.yeoju.go.kr/");
        assertThat(text.getEtcNote()).isNotBlank();
        // 샘플의 신청 링크는 빈 값이라 null
        assertThat(text.getApplyUrl()).isNull();
    }

    @Test
    void KINFA_실제_응답의_원문_필드_5개가_DTO로_역직렬화된다() throws IOException {
        KinfaItem item = kinfaItem();

        assertThat(item.crdtSc()).isNotNull();
        assertThat(item.cnpl()).isNotNull();
        assertThat(item.rltSite()).isNotNull();
        assertThat(item.etcRefSbjc()).isNotNull();
        assertThat(item.mgmDln()).isEqualTo("상시");
    }

    @Test
    void KINFA_실제_응답이_PolicyText_주요_컬럼으로_매핑된다() throws IOException {
        PolicyText text = KinfaPolicyMapper.toPolicy(kinfaItem()).getText();

        assertThat(text.getSupportContent()).contains("대출 한도: 7000만원", "금리: 4.5%", "상환 방법: 원(리)금균등분할상환");
        assertThat(text.getEligibilityDetail()).contains("대상: 사업자, 금융취약계층", "신용 조건: 개인신용평점 하위 20%");
        assertThat(text.getIncomeCondition()).isEqualTo("상환능력 심사를 위한 소득증빙 필요");
        assertThat(text.getAgeCondition()).isEqualTo("미성년자 제외");
        assertThat(text.getApplyMethod()).isEqualTo("미소금융 전국 지점 방문");
        assertThat(text.getApplyPeriod()).isEqualTo("상시");
        assertThat(text.getContactInfo()).contains("문의처: ", "관련 사이트: www.kinfa.or.kr");
        assertThat(text.getEtcNote()).startsWith("대출한도 : 7천만원");
    }
}
