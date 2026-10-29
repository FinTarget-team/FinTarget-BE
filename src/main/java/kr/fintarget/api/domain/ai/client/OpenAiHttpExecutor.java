package kr.fintarget.api.domain.ai.client;

import kr.fintarget.api.common.config.OpenAiProperties;
import kr.fintarget.api.domain.ai.client.AiClientException.Reason;
import kr.fintarget.api.domain.ai.client.dto.OpenAiErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * OpenAI API 공통 실행부: 키 검사, 재시도, 예외 매핑, 실패 로그.
 * 텍스트 생성(OpenAiResponsesClient)과 이후 추가할 임베딩 클라이언트가 함께 쓴다.
 *
 * <p>로그와 예외에는 HTTP 상태, error.type, error.code, 소요 시간만 남긴다.
 * 요청·응답 본문, error.message, API 키는 어디에도 남기지 않는다.
 *
 * <p>재시도: 429(호출 한도), 503, 그 밖의 5xx, 연결 실패만. 다음 경우에는 재시도하지 않는다.
 * <ul>
 *   <li>4xx, 잔액·사용 한도 초과 429: 재시도해도 복구되지 않음 (OpenAI 오류 코드 문서)</li>
 *   <li>읽기 타임아웃: 생성이 이미 진행됐을 수 있어 비용과 대기 시간이 두 배가 됨</li>
 *   <li>Retry-After가 MAX_RETRY_AFTER보다 긺: 사용자가 동기로 기다리는 중</li>
 *   <li>첫 시도부터의 누적 시간 + 다음 대기가 openai.total-timeout 이상</li>
 * </ul>
 */
@Slf4j
@Component
class OpenAiHttpExecutor {

    static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(5);
    // 429 중 재시도해도 복구되지 않는 잔액·사용 한도 초과 코드 (2026-10 OpenAI 오류 코드 문서 기준)
    private static final Set<String> QUOTA_ERROR_CODES = Set.of(
            "insufficient_quota",
            "credit_balance_exhausted",
            "organization_spend_limit_exceeded",
            "project_spend_limit_exceeded",
            "organization_usage_limit_exceeded");
    private static final String QUOTA_ERROR_TYPE = "insufficient_quota";
    // error.type, error.code가 식별자 형태가 아니면 로그에 남기지 않는다
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    private final RestClient openAiRestClient;
    private final OpenAiProperties properties;

    // 테스트에서 대기 없이, 시간을 조작해 돌릴 수 있도록 final이 아님
    private Sleeper sleeper = duration -> Thread.sleep(duration);
    private LongSupplier nanoTime = System::nanoTime;

    OpenAiHttpExecutor(@Qualifier("openAiRestClient") RestClient openAiRestClient, OpenAiProperties properties) {
        this.openAiRestClient = openAiRestClient;
        this.properties = properties;
        if (!properties.hasApiKey()) {
            log.warn("OpenAI API 키(OPENAI_API_KEY)가 설정되지 않아 AI 기능이 비활성화됩니다. AI 호출은 503으로 응답합니다.");
        }
    }

    OpenAiProperties properties() {
        return properties;
    }

    /**
     * @param operation 로그에 남길 호출 이름 (예: "responses")
     * @throws AiUnavailableException 키 없음, 호출 한도·잔액 초과, OpenAI 과부하
     * @throws AiCallFailedException  그 밖의 실패
     */
    <T> OpenAiCallResult<T> post(String operation, String path, Object requestBody, Class<T> responseType) {
        if (!properties.hasApiKey()) {
            throw AiClientException.of(Reason.DISABLED);
        }

        long startedAt = nanoTime.getAsLong();
        for (int attempt = 1; ; attempt++) {
            Failure failure;
            try {
                T body = call(path, requestBody, responseType);
                if (body != null) {
                    return new OpenAiCallResult<>(body, elapsed(startedAt).toMillis(), attempt);
                }
                failure = Failure.of(Reason.INVALID_RESPONSE, false);
            } catch (RestClientResponseException e) {
                failure = fromResponse(e);
            } catch (ResourceAccessException e) {
                failure = fromIoError(e);
            } catch (RestClientException e) {
                // 응답 역직렬화 실패 등
                failure = Failure.of(Reason.INVALID_RESPONSE, false);
            }

            Duration elapsed = elapsed(startedAt);
            if (!failure.retryable() || attempt > properties.maxRetries()) {
                throw fail(operation, failure, attempt, elapsed, failure.retryable() ? "재시도 횟수 소진" : "재시도 대상 아님");
            }
            Duration delay = retryDelay(failure, attempt);
            if (delay == null) {
                throw fail(operation, failure, attempt, elapsed, "Retry-After가 상한(" + MAX_RETRY_AFTER.toSeconds() + "s)보다 김");
            }
            if (elapsed.plus(delay).compareTo(properties.totalTimeout()) >= 0) {
                throw fail(operation, failure, attempt, elapsed, "전체 시간 상한(" + properties.totalTimeout().toMillis() + "ms) 초과");
            }

            log.warn("OpenAI {} 호출 실패, 재시도합니다 ({}/{}) reason={} status={} errorType={} errorCode={} elapsedMs={} delayMs={}",
                    operation, attempt, properties.maxRetries(), failure.reason(), failure.status(),
                    failure.errorType(), failure.errorCode(), elapsed.toMillis(), delay.toMillis());
            sleep(delay);
        }
    }

    private <T> T call(String path, Object requestBody, Class<T> responseType) {
        return openAiRestClient.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(responseType);
    }

    private Failure fromResponse(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        OpenAiErrorResponse.Error error = parseError(e);
        String type = safeIdentifier(error == null ? null : error.type());
        String code = safeIdentifier(error == null ? null : error.code());

        if (status == 429) {
            // Set.of().contains(null)은 NPE를 던지므로 null을 먼저 거른다
            if (QUOTA_ERROR_TYPE.equals(type) || (code != null && QUOTA_ERROR_CODES.contains(code))) {
                return new Failure(Reason.QUOTA_EXCEEDED, false, null, status, type, code);
            }
            return new Failure(Reason.RATE_LIMITED, true, retryAfter(e), status, type, code);
        }
        if (status == 503) {
            return new Failure(Reason.OVERLOADED, true, retryAfter(e), status, type, code);
        }
        if (status >= 500) {
            return new Failure(Reason.SERVER_ERROR, true, retryAfter(e), status, type, code);
        }
        if (status == 401 || status == 403) {
            return new Failure(Reason.AUTH_FAILED, false, null, status, type, code);
        }
        return new Failure(Reason.REQUEST_REJECTED, false, null, status, type, code);
    }

    private static Failure fromIoError(ResourceAccessException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            // HttpConnectTimeoutException은 HttpTimeoutException의 하위 타입이라 먼저 확인한다
            if (cause instanceof HttpConnectTimeoutException || cause instanceof ConnectException
                    || cause instanceof UnknownHostException) {
                return Failure.of(Reason.CONNECTION_FAILED, true);
            }
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return Failure.of(Reason.TIMEOUT, false);
            }
        }
        // 연결 후 끊김 등: 요청이 처리됐을 수 있어 재시도하지 않는다
        return Failure.of(Reason.CONNECTION_FAILED, false);
    }

    private static OpenAiErrorResponse.Error parseError(RestClientResponseException e) {
        try {
            OpenAiErrorResponse response = e.getResponseBodyAs(OpenAiErrorResponse.class);
            return response == null ? null : response.error();
        } catch (RuntimeException ignored) {
            // 오류 본문이 JSON이 아니면 상태 코드만으로 판단한다
            return null;
        }
    }

    private static Duration retryAfter(RestClientResponseException e) {
        HttpHeaders headers = e.getResponseHeaders();
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            double seconds = Double.parseDouble(value.trim());
            return seconds < 0 ? null : Duration.ofMillis((long) Math.ceil(seconds * 1000));
        } catch (NumberFormatException ignored) {
            // HTTP 날짜 형식은 지원하지 않고 기본 백오프를 쓴다
            return null;
        }
    }

    /**
     * @return 다음 시도 전 대기 시간. Retry-After가 상한보다 길면 null
     */
    private Duration retryDelay(Failure failure, int attempt) {
        if (failure.retryAfter() != null) {
            return failure.retryAfter().compareTo(MAX_RETRY_AFTER) > 0 ? null : failure.retryAfter();
        }
        // 지수 백오프(1배, 2배, 4배...) + ±20% 지터
        long baseMillis = properties.retryBackoff().toMillis() << (attempt - 1);
        if (baseMillis <= 0) {
            return Duration.ZERO;
        }
        double jitter = ThreadLocalRandom.current().nextDouble(0.8, 1.2);
        return Duration.ofMillis(Math.round(baseMillis * jitter));
    }

    private AiClientException fail(String operation, Failure failure, int attempts, Duration elapsed, String why) {
        String format = "OpenAI {} 호출 실패 ({}) reason={} status={} errorType={} errorCode={} attempts={} elapsedMs={}";
        Object[] args = {operation, why, failure.reason(), failure.status(), failure.errorType(), failure.errorCode(),
                attempts, elapsed.toMillis()};
        // 키·요청 형식 문제는 운영 설정 오류라 error로 남긴다
        if (failure.reason() == Reason.AUTH_FAILED || failure.reason() == Reason.REQUEST_REJECTED) {
            log.error(format, args);
        } else {
            log.warn(format, args);
        }
        return AiClientException.of(failure.reason());
    }

    private void sleep(Duration duration) {
        if (duration.isZero()) {
            return;
        }
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw AiClientException.of(Reason.INTERRUPTED);
        }
    }

    private Duration elapsed(long startedAt) {
        return Duration.ofNanos(nanoTime.getAsLong() - startedAt);
    }

    private static String safeIdentifier(String value) {
        if (value == null) {
            return null;
        }
        return SAFE_IDENTIFIER.matcher(value).matches() ? value : "unrecognized";
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private record Failure(Reason reason, boolean retryable, Duration retryAfter,
                           Integer status, String errorType, String errorCode) {

        static Failure of(Reason reason, boolean retryable) {
            return new Failure(reason, retryable, null, null, null, null);
        }
    }
}
