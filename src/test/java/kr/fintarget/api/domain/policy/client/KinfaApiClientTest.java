package kr.fintarget.api.domain.policy.client;

import kr.fintarget.api.domain.policy.client.dto.KinfaItem;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class KinfaApiClientTest {

    @Test
    void 최신_기준연월만_전체_페이지를_받아_운영중인_상품만_반환한다() {
        // 기준연월 필터 없는 첫 조회 → 최신 202609 확인, 이후 basYm=202609로 150건(2페이지) 조회
        List<URI> requested = new ArrayList<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("https://example.com/getOrdinaryFinanceInfo")
                .exchangeFunction(request -> {
                    requested.add(request.url());
                    String basYm = queryParam(request.url(), "basYm");
                    int pageNo = Integer.parseInt(queryParam(request.url(), "pageNo"));
                    int numOfRows = Integer.parseInt(queryParam(request.url(), "numOfRows"));
                    String body;
                    if (basYm == null) {
                        body = response(10_803, List.of(itemJson("202609", "기관", "상품0", "Y")));
                    } else {
                        int size = pageNo == 1 ? numOfRows : 50;
                        List<String> items = IntStream.range(0, size)
                                // 각 페이지 마지막 항목은 판매 중단(N)
                                .mapToObj(i -> itemJson(basYm, "기관", "상품" + pageNo + "-" + i, i == size - 1 ? "N" : "Y"))
                                .collect(Collectors.toList());
                        body = response(150, items);
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(body).build());
                })
                .build();
        KinfaApiClient client = new KinfaApiClient(webClient);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");

        List<KinfaItem> result = client.fetchLatestActiveProducts();

        assertThat(requested).hasSize(3);
        assertThat(queryParam(requested.get(0), "basYm")).isNull();
        assertThat(queryParam(requested.get(1), "basYm")).isEqualTo("202609");
        assertThat(queryParam(requested.get(2), "pageNo")).isEqualTo("2");
        assertThat(result).hasSize(148);
        assertThat(result).allMatch(item -> "Y".equals(item.prdExisYn()) && "202609".equals(item.basYm()));
    }

    private static String response(int totalCount, List<String> items) {
        return "{\"response\":{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL SERVICE.\"},\"body\":{"
                + "\"totalCount\":" + totalCount + ",\"items\":{\"item\":[" + String.join(",", items) + "]}}}}";
    }

    private static String itemJson(String basYm, String ofrInstNm, String finPrdNm, String prdExisYn) {
        return "{\"basYm\":\"" + basYm + "\",\"snq\":\"1\",\"ofrInstNm\":\"" + ofrInstNm + "\",\"finPrdNm\":\""
                + finPrdNm + "\",\"prdExisYn\":\"" + prdExisYn + "\"}";
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
