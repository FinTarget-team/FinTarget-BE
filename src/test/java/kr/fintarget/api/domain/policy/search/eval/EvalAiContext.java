package kr.fintarget.api.domain.policy.search.eval;

import kr.fintarget.api.common.config.OpenAiConfig;
import kr.fintarget.api.domain.ai.client.AiTextClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

/**
 * 평가 실행기가 실제 OpenAI 클라이언트(임베딩·생성)를 쓸 때의 빈 구성 (#184). #126 PolicyQaEvalLiveRun과 같은 방식이다.
 * 비용이 들어 OPENAI_API_KEY와 RUN_AI_EVAL=true가 모두 있을 때만 만든다.
 */
public final class EvalAiContext {

    public static final String ENV_API_KEY = "OPENAI_API_KEY";
    public static final String ENV_RUN = "RUN_AI_EVAL";

    private EvalAiContext() {
    }

    public static boolean liveCallsAllowed() {
        String key = System.getenv(ENV_API_KEY);
        return "true".equals(System.getenv(ENV_RUN)) && key != null && !key.isBlank();
    }

    /**
     * 운영과 같은 AI 클라이언트 빈으로 body를 실행한다. 컨텍스트는 body가 끝나면 닫힌다.
     *
     * @throws IllegalStateException 실호출이 허용되지 않았을 때
     */
    public static void run(Body body) {
        if (!liveCallsAllowed()) {
            throw new IllegalStateException(ENV_API_KEY + "와 " + ENV_RUN + "=true가 있어야 OpenAI를 호출합니다");
        }
        new ApplicationContextRunner()
                .withBean(RestClient.Builder.class, RestClient::builder)
                .withUserConfiguration(Config.class)
                .withPropertyValues("openai.api-key=" + System.getenv(ENV_API_KEY))
                .run(context -> {
                    if (context.getStartupFailure() != null) {
                        throw context.getStartupFailure();
                    }
                    body.accept(context);
                });
    }

    @FunctionalInterface
    public interface Body {
        void accept(ApplicationContext context) throws Exception;
    }

    // OpenAiHttpExecutor가 패키지 전용이라 클래스 대신 패키지를 스캔한다
    @Configuration
    @Import(OpenAiConfig.class)
    @ComponentScan(basePackageClasses = AiTextClient.class)
    static class Config {
    }
}
