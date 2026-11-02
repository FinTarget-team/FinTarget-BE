package kr.fintarget.mockbank.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 핀타겟 메인 서버 웹훅 설정.
 *
 * @param baseUrl        메인 서버 주소 (예: http://54.180.93.46:8080)
 * @param webhookKey     메인 서버 BANK_WEBHOOK_KEY와 같은 값. X-Bank-Webhook-Key 헤더로 보낸다
 * @param connectTimeout 연결 타임아웃
 * @param readTimeout    응답 타임아웃
 */
@ConfigurationProperties(prefix = "fintarget")
public record FinTargetProperties(String baseUrl, String webhookKey, Duration connectTimeout, Duration readTimeout) {

    public FinTargetProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("FINTARGET_BASE_URL 환경변수를 설정해 주세요.");
        }
        if (webhookKey == null || webhookKey.isBlank()) {
            throw new IllegalStateException("FINTARGET_WEBHOOK_KEY 환경변수를 설정해 주세요.");
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(10);
        }
    }
}
