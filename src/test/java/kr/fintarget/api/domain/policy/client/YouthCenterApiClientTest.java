package kr.fintarget.api.domain.policy.client;

import kr.fintarget.api.domain.policy.client.dto.YouthCenterPolicyItem;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class YouthCenterApiClientTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    private YouthCenterPolicyItem item(String plcyNo, String lclsfNm, String aplyPrdSeCd, String aplyYmd) {
        return new YouthCenterPolicyItem(plcyNo, "정책", null, null, lclsfNm, null, null, null, null,
                null, null, null, aplyYmd, null, null, null, null, aplyPrdSeCd);
    }

    @Test
    void 마감된_정책은_제외한다() {
        assertThat(YouthCenterApiClient.isOpen(item("1", "주거", "0057003", ""), TODAY)).isFalse();
    }

    @Test
    void 상시_정책은_포함한다() {
        assertThat(YouthCenterApiClient.isOpen(item("1", "주거", "0057002", ""), TODAY)).isTrue();
    }

    @Test
    void 특정기간_정책은_마지막_종료일이_오늘_이후면_포함하고_지났으면_제외한다() {
        assertThat(YouthCenterApiClient.isOpen(item("1", "주거", "0057001", "20260101 ~ 20261012"), TODAY)).isTrue();
        assertThat(YouthCenterApiClient.isOpen(item("1", "주거", "0057001", "20260101 ~ 20261011"), TODAY)).isFalse();
        // 여러 기간이 \N으로 이어지면 가장 늦은 종료일 기준
        assertThat(YouthCenterApiClient.isOpen(
                item("1", "주거", "0057001", "20250101 ~ 20251231\\N20260101 ~ 20271231"), TODAY)).isTrue();
    }

    @Test
    void 전체_페이지를_순회해_주거_금융이면서_열려있는_정책만_반환한다() {
        // 총 250건 = 3페이지 (100, 100, 50). 각 페이지 첫 항목만 주거, 나머지는 일자리
        List<URI> requested = new ArrayList<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("https://example.com/getPlcy")
                .exchangeFunction(request -> {
                    requested.add(request.url());
                    int pageNum = Integer.parseInt(queryParam(request.url(), "pageNum"));
                    int size = pageNum < 3 ? 100 : 50;
                    String items = IntStream.range(0, size)
                            .mapToObj(i -> policyJson("P" + pageNum + "-" + i, i == 0 ? "주거" : "일자리"))
                            .collect(Collectors.joining(","));
                    String body = "{\"resultCode\":200,\"result\":{\"pagging\":{\"totCount\":250,\"pageNum\":" + pageNum
                            + ",\"pageSize\":100},\"youthPolicyList\":[" + items + "]}}";
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(body).build());
                })
                .build();
        YouthCenterApiClient client = new YouthCenterApiClient(webClient);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");

        List<YouthCenterPolicyItem> result = client.fetchOpenHousingAndFinancePolicies(TODAY);

        assertThat(requested).hasSize(3);
        assertThat(queryParam(requested.get(0), "apiKeyNm")).isEqualTo("test-key");
        assertThat(queryParam(requested.get(0), "rtnType")).isEqualTo("json");
        assertThat(result).extracting(YouthCenterPolicyItem::plcyNo).containsExactly("P1-0", "P2-0", "P3-0");
    }

    private static String policyJson(String plcyNo, String lclsfNm) {
        return "{\"plcyNo\":\"" + plcyNo + "\",\"plcyNm\":\"정책\",\"lclsfNm\":\"" + lclsfNm
                + "\",\"aplyPrdSeCd\":\"0057002\",\"aplyYmd\":\"\"}";
    }

    private static String queryParam(URI uri, String name) {
        for (String pair : uri.getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals(name)) {
                return kv.length > 1 ? kv[1] : "";
            }
        }
        return null;
    }
}
