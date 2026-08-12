package kr.fintarget.api.domain.policy.client;

import kr.fintarget.api.domain.policy.client.dto.YouthCenterApiResponse;
import kr.fintarget.api.domain.policy.client.dto.YouthCenterPolicyItem;
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
 * 온통청년(youthcenter.go.kr) 정책 목록 API 클라이언트.
 * 정책대분류명이 "주거", "금융"(금융･복지･문화) 인 정책만 응답 후 필터링해 반환한다.
 */
@Slf4j
@Component
public class YouthCenterApiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient youthCenterWebClient;

    @Value("${youthcenter.api.key}")
    private String apiKey;

    public YouthCenterApiClient(@Qualifier("youthCenterWebClient") WebClient youthCenterWebClient) {
        this.youthCenterWebClient = youthCenterWebClient;
    }

    public List<YouthCenterPolicyItem> fetchHousingAndFinancePolicies(int pageIndex, int display) {
        YouthCenterApiResponse response = call(pageIndex, display);

        if (response.result() == null || response.result().youthPolicyList() == null) {
            return List.of();
        }

        return response.result().youthPolicyList().stream()
                .filter(YouthCenterApiClient::isHousingOrFinance)
                .toList();
    }

    private static boolean isHousingOrFinance(YouthCenterPolicyItem item) {
        String category = item.lclsfNm();
        return category != null && (category.contains("주거") || category.contains("금융"));
    }

    private YouthCenterApiResponse call(int pageIndex, int display) {
        try {
            YouthCenterApiResponse response = youthCenterWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("openApiVlak", apiKey)
                            .queryParam("pageIndex", pageIndex)
                            .queryParam("display", display)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> Mono.error(new YouthCenterApiException(
                                            "온통청년 API 요청 오류(" + clientResponse.statusCode() + "): " + body))))
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> Mono.error(new YouthCenterApiException(
                                            "온통청년 API 서버 오류(" + clientResponse.statusCode() + "): " + body))))
                    .bodyToMono(YouthCenterApiResponse.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response == null) {
                throw new YouthCenterApiException("온통청년 API 응답이 비어 있습니다.");
            }
            return response;
        } catch (YouthCenterApiException e) {
            log.error("온통청년 API 호출 실패: {}", e.getMessage());
            throw e;
        } catch (WebClientRequestException e) {
            log.error("온통청년 API 연결 실패(네트워크/타임아웃)", e);
            throw new YouthCenterApiException("온통청년 API 연결에 실패했습니다.", e);
        } catch (WebClientResponseException e) {
            log.error("온통청년 API 응답 오류: {}", e.getStatusCode(), e);
            throw new YouthCenterApiException("온통청년 API 응답 오류: " + e.getStatusCode(), e);
        } catch (RuntimeException e) {
            log.error("온통청년 API 호출 중 알 수 없는 오류(타임아웃 포함 가능)", e);
            throw new YouthCenterApiException("온통청년 API 호출에 실패했습니다.", e);
        }
    }
}
