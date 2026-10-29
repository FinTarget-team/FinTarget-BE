package kr.fintarget.api.domain.ai.client;

import kr.fintarget.api.common.config.OpenAiProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 OpenAI API를 호출해 연동을 검증한다. OPENAI_API_KEY가 없으면 skip되어 CI에 영향이 없다.
 * Spring 컨텍스트와 DB를 띄우지 않고 OpenAiProperties·클라이언트를 직접 생성해 호출한다.
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiResponsesClientLiveCallTest {

    // reasoningEffort는 null로 둬 코드 기본값(low, 두 모델이 공통 지원하는 최저값)을 쓴다.
    // 추론 토큰이 상한을 다 써버리면 incomplete가 나므로 너무 낮게 잡지 않는다.
    private static final int MAX_OUTPUT_TOKENS = 200;

    @Test
    void 실제_OpenAI_호출_시_응답_텍스트가_비어있지_않다() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        OpenAiProperties properties = new OpenAiProperties(apiKey, null, null, null,
                MAX_OUTPUT_TOKENS, null, null, null, null, null);

        RestClient restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
        OpenAiHttpExecutor executor = new OpenAiHttpExecutor(restClient, properties);
        AiTextClient client = new OpenAiResponsesClient(executor);

        AiTextRequest request = new AiTextRequest(null, List.of(AiMessage.user("한 문장으로 인사해줘")), null);

        Instant startedAt = Instant.now();
        AiTextResult result = client.generate(request);
        Duration elapsed = Duration.between(startedAt, Instant.now());

        log.info("OpenAI 실호출 검증 완료 model={} outputTokens={} reasoningTokens={} elapsedMs={}",
                result.model(), result.outputTokens(), result.reasoningTokens(), elapsed.toMillis());

        assertThat(result.text())
                .withFailMessage("OpenAI 응답 텍스트가 비어 있습니다 (model=%s)", result.model())
                .isNotBlank();
    }
}
