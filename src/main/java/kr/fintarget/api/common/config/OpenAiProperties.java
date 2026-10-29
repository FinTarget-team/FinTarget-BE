package kr.fintarget.api.common.config;

import kr.fintarget.api.domain.ai.client.AiModelTier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;

import java.time.Duration;

/**
 * OpenAI 호출 설정 (openai.*).
 * 서버 application.yml에 openai 항목이 없어도 기동되도록 모든 값에 코드 기본값을 둔다.
 * api-key가 비어 있으면 서버는 정상 기동하고, AI 호출 시점에 AiUnavailableException(503)이 발생한다.
 */
@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        String apiKey,
        String baseUrl,
        Model model,
        String reasoningEffort,
        Integer maxOutputTokens,
        Duration connectTimeout,
        Duration readTimeout,
        // 첫 시도부터의 누적 시간 상한. 넘으면 재시도하지 않는다
        Duration totalTimeout,
        Integer maxRetries,
        Duration retryBackoff
) {

    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    // 두 모델(gpt-6-luna, gpt-6.1-sol)이 공통으로 지원하는 가장 낮은 값 (Sol은 none, minimal 미지원)
    public static final String DEFAULT_REASONING_EFFORT = "low";
    // 추론 토큰을 포함한 상한
    public static final int DEFAULT_MAX_OUTPUT_TOKENS = 4000;

    public OpenAiProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        baseUrl = isBlank(baseUrl) ? DEFAULT_BASE_URL : baseUrl.trim();
        model = model == null ? new Model(null, null) : model;
        reasoningEffort = isBlank(reasoningEffort) ? DEFAULT_REASONING_EFFORT : reasoningEffort.trim();
        maxOutputTokens = maxOutputTokens == null ? DEFAULT_MAX_OUTPUT_TOKENS : maxOutputTokens;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
        totalTimeout = totalTimeout == null ? Duration.ofSeconds(45) : totalTimeout;
        maxRetries = maxRetries == null ? 2 : maxRetries;
        retryBackoff = retryBackoff == null ? Duration.ofSeconds(1) : retryBackoff;
    }

    public boolean hasApiKey() {
        return !apiKey.isEmpty();
    }

    // 기본 record toString은 apiKey를 그대로 출력하므로 덮어쓴다
    @Override
    public String toString() {
        return "OpenAiProperties[apiKey=" + (hasApiKey() ? "****" : "(empty)")
                + ", baseUrl=" + baseUrl + ", model=" + model + ", reasoningEffort=" + reasoningEffort
                + ", maxOutputTokens=" + maxOutputTokens + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + ", totalTimeout=" + totalTimeout
                + ", maxRetries=" + maxRetries + ", retryBackoff=" + retryBackoff + "]";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 모델 ID. openai.model.default(기본), openai.model.premium(상위)
     */
    public record Model(
            @Name("default") String defaultId,
            String premium
    ) {

        public static final String DEFAULT_ID = "gpt-6-luna";
        public static final String PREMIUM_ID = "gpt-6.1-sol";

        public Model {
            defaultId = isBlank(defaultId) ? DEFAULT_ID : defaultId.trim();
            premium = isBlank(premium) ? PREMIUM_ID : premium.trim();
        }

        public String idOf(AiModelTier tier) {
            return tier == AiModelTier.PREMIUM ? premium : defaultId;
        }
    }
}
