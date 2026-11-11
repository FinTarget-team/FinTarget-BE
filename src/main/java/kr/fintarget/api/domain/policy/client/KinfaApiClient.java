package kr.fintarget.api.domain.policy.client;

import kr.fintarget.api.domain.policy.client.dto.KinfaApiResponse;
import kr.fintarget.api.domain.policy.client.dto.KinfaBody;
import kr.fintarget.api.domain.policy.client.dto.KinfaItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 서민금융진흥원(KINFA) 서민금융상품기본정보 API 클라이언트.
 * 응답은 기준연월(basYm)별 스냅샷이 누적된 형태라, 가장 최근 기준연월의 전체 페이지만 받아
 * prdExisYn="Y"(현재 운영 중)인 상품만 반환한다.
 */
@Slf4j
@Component
public class KinfaApiClient {

    private static final String SUCCESS_RESULT_CODE = "00";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int PAGE_SIZE = 100;
    // 무한 루프/과다 호출 방지용 상한 (2026-10 기준 한 달치 329건 = 4페이지)
    private static final int MAX_PAGES = 20;

    private final WebClient kinfaWebClient;

    @Value("${kinfa.api.key}")
    private String apiKey;

    public KinfaApiClient(@Qualifier("kinfaWebClient") WebClient kinfaWebClient) {
        this.kinfaWebClient = kinfaWebClient;
    }

    public List<KinfaItem> fetchLatestActiveProducts() {
        String latestBasYm = fetchLatestBasYm();
        if (latestBasYm == null) {
            return List.of();
        }

        List<KinfaItem> collected = new ArrayList<>();
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            KinfaBody body = bodyOf(call(pageNo, PAGE_SIZE, latestBasYm));
            List<KinfaItem> items = itemsOf(body);
            if (items.isEmpty()) {
                break;
            }
            collected.addAll(items);

            Integer totalCount = body.totalCount();
            if (totalCount == null || collected.size() >= totalCount) {
                break;
            }
            if (pageNo == MAX_PAGES) {
                log.warn("KINFA 상품 조회가 최대 페이지({})에 도달해 일부만 가져왔습니다. 기준연월 {}, 전체 {}건 중 {}건",
                        MAX_PAGES, latestBasYm, totalCount, collected.size());
            }
        }

        return collected.stream()
                .filter(KinfaApiClient::isActive)
                .toList();
    }

    // 기준연월 필터 없이 조회하면 최신 기준연월 데이터가 먼저 온다 (2026-10 실데이터 기준 확인)
    private String fetchLatestBasYm() {
        List<KinfaItem> first = itemsOf(bodyOf(call(1, 1, null)));
        return first.isEmpty() ? null : first.get(0).basYm();
    }

    private static KinfaBody bodyOf(KinfaApiResponse response) {
        return response.response() == null ? null : response.response().body();
    }

    private static List<KinfaItem> itemsOf(KinfaBody body) {
        if (body == null || body.items() == null || body.items().item() == null) {
            return List.of();
        }
        return body.items().item();
    }

    private static boolean isActive(KinfaItem item) {
        return "Y".equals(item.prdExisYn());
    }

    private KinfaApiResponse call(int pageNo, int numOfRows, String basYm) {
        try {
            KinfaApiResponse response = kinfaWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("serviceKey", apiKey)
                            .queryParam("pageNo", pageNo)
                            .queryParam("numOfRows", numOfRows)
                            .queryParam("resultType", "json")
                            .queryParamIfPresent("basYm", Optional.ofNullable(basYm))
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> Mono.error(new KinfaApiException(
                                            "KINFA API 요청 오류(" + clientResponse.statusCode() + "): " + body))))
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> Mono.error(new KinfaApiException(
                                            "KINFA API 서버 오류(" + clientResponse.statusCode() + "): " + body))))
                    .bodyToMono(KinfaApiResponse.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response == null || response.response() == null || response.response().header() == null) {
                throw new KinfaApiException("KINFA API 응답이 비어 있습니다.");
            }
            if (!SUCCESS_RESULT_CODE.equals(response.response().header().resultCode())) {
                throw new KinfaApiException("KINFA API 오류 응답: " + response.response().header().resultMsg());
            }
            return response;
        } catch (KinfaApiException e) {
            log.error("KINFA API 호출 실패: {}", e.getMessage());
            throw e;
        } catch (WebClientRequestException e) {
            log.error("KINFA API 연결 실패(네트워크/타임아웃)", e);
            throw new KinfaApiException("KINFA API 연결에 실패했습니다.", e);
        } catch (WebClientResponseException e) {
            log.error("KINFA API 응답 오류: {}", e.getStatusCode(), e);
            throw new KinfaApiException("KINFA API 응답 오류: " + e.getStatusCode(), e);
        } catch (RuntimeException e) {
            log.error("KINFA API 호출 중 알 수 없는 오류(타임아웃 포함 가능)", e);
            throw new KinfaApiException("KINFA API 호출에 실패했습니다.", e);
        }
    }
}
