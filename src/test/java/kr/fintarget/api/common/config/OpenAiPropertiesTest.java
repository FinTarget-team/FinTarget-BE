package kr.fintarget.api.common.config;

import kr.fintarget.api.domain.ai.client.AiModelTier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiPropertiesTest {

    private static OpenAiProperties bind(Map<String, String> source) {
        return new Binder(new MapConfigurationPropertySource(source)).bindOrCreate("openai", OpenAiProperties.class);
    }

    @Test
    void openai_항목이_없으면_코드_기본값을_쓴다() {
        OpenAiProperties properties = bind(Map.of());

        assertThat(properties.hasApiKey()).isFalse();
        assertThat(properties.baseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(properties.model().idOf(AiModelTier.DEFAULT)).isEqualTo("gpt-6-luna");
        assertThat(properties.model().idOf(AiModelTier.PREMIUM)).isEqualTo("gpt-6.1-sol");
        assertThat(properties.reasoningEffort()).isEqualTo("low");
        assertThat(properties.maxOutputTokens()).isEqualTo(4000);
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.totalTimeout()).isEqualTo(Duration.ofSeconds(45));
        assertThat(properties.maxRetries()).isEqualTo(2);
        assertThat(properties.retryBackoff()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void 설정값을_바인딩한다() {
        OpenAiProperties properties = bind(Map.of(
                "openai.api-key", "sk-test",
                "openai.model.default", "model-a",
                "openai.model.premium", "model-b",
                "openai.reasoning-effort", "medium",
                "openai.max-output-tokens", "2000",
                "openai.total-timeout", "10s",
                "openai.max-retries", "0"));

        assertThat(properties.hasApiKey()).isTrue();
        assertThat(properties.model().idOf(AiModelTier.DEFAULT)).isEqualTo("model-a");
        assertThat(properties.model().idOf(AiModelTier.PREMIUM)).isEqualTo("model-b");
        assertThat(properties.reasoningEffort()).isEqualTo("medium");
        assertThat(properties.maxOutputTokens()).isEqualTo(2000);
        assertThat(properties.totalTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.maxRetries()).isZero();
    }

    @Test
    void 빈_값은_기본값으로_대체하고_공백_키는_키_없음으로_본다() {
        // ${OPENAI_API_KEY:}, ${OPENAI_MODEL_DEFAULT:...}에 빈 환경변수가 들어온 경우
        OpenAiProperties properties = bind(Map.of(
                "openai.api-key", "   ",
                "openai.model.default", "",
                "openai.reasoning-effort", " "));

        assertThat(properties.hasApiKey()).isFalse();
        assertThat(properties.model().idOf(AiModelTier.DEFAULT)).isEqualTo("gpt-6-luna");
        assertThat(properties.reasoningEffort()).isEqualTo("low");
    }

    @Test
    void toString에_API_키가_나오지_않는다() {
        OpenAiProperties properties = bind(Map.of("openai.api-key", "sk-test-SECRET"));

        assertThat(properties.toString()).doesNotContain("sk-test-SECRET").contains("apiKey=****");
    }
}
