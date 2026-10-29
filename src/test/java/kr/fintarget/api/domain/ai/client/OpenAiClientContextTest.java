package kr.fintarget.api.domain.ai.client;

import kr.fintarget.api.common.config.OpenAiConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * openai 설정이 없는 서버(EC2 application.yml에 항목 없음, OPENAI_API_KEY 없음)에서도
 * 빈이 만들어지고, 호출 시점에만 503 예외가 나는지 확인한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class OpenAiClientContextTest {

    // RestClient.Builder는 운영에서 spring-boot-starter-restclient 자동 설정이 제공한다 (#105)
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(OpenAiConfig.class, OpenAiHttpExecutor.class, OpenAiResponsesClient.class);

    @Test
    void 설정과_키가_없어도_기동하고_호출하면_AiUnavailableException(CapturedOutput output) {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            AiTextClient client = context.getBean(AiTextClient.class);

            Throwable thrown = catchThrowable(() ->
                    client.generate(new AiTextRequest(null, List.of(AiMessage.user("질문")), null)));

            assertThat(thrown).isInstanceOf(AiUnavailableException.class).hasMessage(AiUnavailableException.MESSAGE);
            assertThat(((AiClientException) thrown).getReason()).isEqualTo(AiClientException.Reason.DISABLED);
        });
        assertThat(output.getAll().split("AI 기능이 비활성화됩니다", -1)).hasSize(2);
    }

    @Test
    void 키가_있으면_비활성_경고를_남기지_않는다(CapturedOutput output) {
        contextRunner.withPropertyValues("openai.api-key=sk-test").run(context ->
                assertThat(context).hasNotFailed().hasSingleBean(AiTextClient.class));
        assertThat(output.getAll()).doesNotContain("AI 기능이 비활성화됩니다");
    }
}
