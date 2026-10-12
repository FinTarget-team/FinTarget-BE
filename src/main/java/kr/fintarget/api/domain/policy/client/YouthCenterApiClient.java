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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 온통청년(youthcenter.go.kr) 정책 목록 API 클라이언트.
 * 전체 페이지를 순회해, 정책대분류명이 "주거", "금융"(금융･복지･문화)이면서 신청이 끝나지 않은 정책만 반환한다.
 */
@Slf4j
@Component
public class YouthCenterApiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int PAGE_SIZE = 100;
    // 무한 루프/과다 호출 방지용 상한 (2026-10 기준 전체 3,234건 = 33페이지)
    private static final int MAX_PAGES = 100;
    // 온통청년 API는 연속 호출 시 간헐적으로 500(HTML) 또는 400(e002 invalid param data)을 반환하고,
    // 같은 요청을 다시 보내면 성공한다 (2026-10-12 실측). 페이지 단위로 재시도하고 호출 간격을 둔다.
    private static final int MAX_ATTEMPTS = 3;
    // 신청기간구분코드: 0057001 특정기간(aplyYmd에 기간 있음), 0057002 상시, 0057003 마감
    private static final String APPLY_PERIOD_CLOSED = "0057003";
    private static final Pattern YMD_PATTERN = Pattern.compile("\\d{8}");
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

    private final WebClient youthCenterWebClient;

    // 테스트에서 대기 없이 돌릴 수 있도록 final이 아님
    private Duration pageInterval = Duration.ofMillis(200);
    private Duration retryBackoff = Duration.ofSeconds(1);

    @Value("${youthcenter.api.key}")
    private String apiKey;

    public YouthCenterApiClient(@Qualifier("youthCenterWebClient") WebClient youthCenterWebClient) {
        this.youthCenterWebClient = youthCenterWebClient;
    }

    public List<YouthCenterPolicyItem> fetchOpenHousingAndFinancePolicies(LocalDate today) {
        List<YouthCenterPolicyItem> collected = new ArrayList<>();

        for (int pageNum = 1; pageNum <= MAX_PAGES; pageNum++) {
            if (pageNum > 1) {
                sleep(pageInterval);
            }
            YouthCenterApiResponse response = callWithRetry(pageNum, PAGE_SIZE);
            if (response.result() == null || response.result().youthPolicyList() == null
                    || response.result().youthPolicyList().isEmpty()) {
                break;
            }
            collected.addAll(response.result().youthPolicyList());

            Integer totalCount = response.result().pagging() == null ? null : response.result().pagging().totCount();
            if (totalCount == null || collected.size() >= totalCount) {
                break;
            }
            if (pageNum == MAX_PAGES) {
                log.warn("온통청년 정책 조회가 최대 페이지({})에 도달해 일부만 가져왔습니다. 전체 {}건 중 {}건",
                        MAX_PAGES, totalCount, collected.size());
            }
        }

        return collected.stream()
                .filter(YouthCenterApiClient::isHousingOrFinance)
                .filter(item -> isOpen(item, today))
                .toList();
    }

    private YouthCenterApiResponse callWithRetry(int pageNum, int pageSize) {
        for (int attempt = 1; ; attempt++) {
            try {
                return call(pageNum, pageSize);
            } catch (YouthCenterApiException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                log.warn("온통청년 API {}페이지 호출 실패, 재시도합니다 ({}/{}): {}",
                        pageNum, attempt, MAX_ATTEMPTS, e.getMessage());
                sleep(retryBackoff.multipliedBy(attempt));
            }
        }
    }

    private static void sleep(Duration duration) {
        if (duration.isZero()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new YouthCenterApiException("온통청년 API 호출 대기 중 인터럽트되었습니다.", e);
        }
    }

    static boolean isOpen(YouthCenterPolicyItem item, LocalDate today) {
        if (APPLY_PERIOD_CLOSED.equals(item.aplyPrdSeCd())) {
            return false;
        }
        // 특정기간 정책은 aplyYmd("20260101 ~ 20261231", 여러 기간이면 \N으로 구분)의 마지막 종료일 기준
        Matcher matcher = YMD_PATTERN.matcher(item.aplyYmd() == null ? "" : item.aplyYmd());
        LocalDate lastDate = null;
        while (matcher.find()) {
            try {
                LocalDate date = LocalDate.parse(matcher.group(), YMD);
                if (lastDate == null || date.isAfter(lastDate)) {
                    lastDate = date;
                }
            } catch (DateTimeParseException ignored) {
                // 형식이 깨진 날짜는 무시
            }
        }
        return lastDate == null || !lastDate.isBefore(today);
    }

    private static boolean isHousingOrFinance(YouthCenterPolicyItem item) {
        String category = item.lclsfNm();
        return category != null && (category.contains("주거") || category.contains("금융"));
    }

    private YouthCenterApiResponse call(int pageNum, int pageSize) {
        try {
            YouthCenterApiResponse response = youthCenterWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("apiKeyNm", apiKey)
                            .queryParam("pageNum", pageNum)
                            .queryParam("pageSize", pageSize)
                            .queryParam("rtnType", "json")
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
