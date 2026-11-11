package kr.fintarget.api.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application-example.yml은 복사해서 쓰는 설정 템플릿이라 openai.model.default 폴백값이
 * 코드 기본값(Model.DEFAULT_ID)과 달라지면, 템플릿을 쓰는 서버만 조용히 다른 모델로 기동된다 (#131).
 */
class OpenAiModelDefaultTemplateTest {

    private static final Pattern DEFAULT_FALLBACK = Pattern.compile("\\$\\{OPENAI_MODEL_DEFAULT:([^}]*)}");

    @Test
    void application_example_yml의_openai_model_default_폴백값은_코드_기본값과_같다() throws IOException {
        String yaml = new ClassPathResource("application-example.yml").getContentAsString(StandardCharsets.UTF_8);

        Matcher matcher = DEFAULT_FALLBACK.matcher(yaml);
        assertThat(matcher.find())
                .withFailMessage("application-example.yml에서 ${OPENAI_MODEL_DEFAULT:...} 폴백값을 찾지 못했습니다.")
                .isTrue();
        assertThat(matcher.group(1)).isEqualTo(OpenAiProperties.Model.DEFAULT_ID);
    }
}
