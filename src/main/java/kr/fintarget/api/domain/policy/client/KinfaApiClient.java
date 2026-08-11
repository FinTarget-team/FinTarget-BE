package kr.fintarget.api.domain.policy.client;

import kr.fintarget.api.domain.policy.client.dto.KinfaApiResponse;
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
import java.util.List;

/**
 * 서민금융진흥원(KINFA) 서민금융상품기본정보 API 클라이언트.
 * prdExisYn="Y"(현재 운영 중)인 상품만 응답 후 필터링해 반환한다.
 */
@Slf4j
@Component
public class KinfaApiClient {

    private static final String SUCCESS_RESULT_CODE = "00";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient kinfaWebClient;

    @Value("${kinfa.api.key}")
    private String apiKey;

    public KinfaApiClient(@Qualifier("kinfaWebClient") WebClient kinfaWebClient) {
        this.kinfaWebClient = kinfaWebClient;
    }

    public List<KinfaItem> fetchActiveProducts(int pageNo, int numOfRows) {
        KinfaApiResponse response = call(pageNo, numOfRows);

        if (response.response() == null
                || response.response().body() == null
                || response.response().body().items() == null
                || response.response().body().items().item() == null) {
            return List.of();
        }

        return response.response().body().items().item().stream()
                .filter(KinfaApiClient::isActive)
                .toList();
    }

    private static boolean isActive(KinfaItem item) {
        return "Y".equals(item.prdExisYn());
    }

    private KinfaApiResponse call(int pageNo, int numOfRows) {
        try {
            KinfaApiResponse response = kinfaWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("serviceKey", apiKey)
                            .queryParam("pageNo", pageNo)
                            .queryParam("numOfRows", numOfRows)
                            .queryParam("resultType", "json")
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
