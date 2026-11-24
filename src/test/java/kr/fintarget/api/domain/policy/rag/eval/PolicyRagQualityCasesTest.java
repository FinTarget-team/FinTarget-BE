package kr.fintarget.api.domain.policy.rag.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 통합 답변 품질 점검 질문(#235) 파일 형식 검증. 문항을 고치다 깨지지 않게만 확인한다 (실호출은 하지 않음).
 */
class PolicyRagQualityCasesTest {

    @Test
    void 문항_ID가_겹치지_않고_사용자_정규식_거절_표시가_올바르다() throws Exception {
        JsonNode root;
        try (InputStream in = new ClassPathResource("ai-eval/policy-rag-quality-cases.json").getInputStream()) {
            root = new ObjectMapper().readTree(in);
        }
        JsonNode users = root.get("users");
        Set<String> ids = new HashSet<>();
        int refuse = 0;
        for (JsonNode c : root.get("cases")) {
            assertThat(ids.add(c.get("id").asText())).as("중복 ID %s", c.get("id")).isTrue();
            assertThat(users.has(c.get("user").asText())).as("없는 사용자 %s", c.get("user")).isTrue();
            assertThat(c.get("question").asText()).isNotBlank();
            if (c.path("refuse").asBoolean(false)) {
                refuse++;
                assertThat(c.has("expectPolicy")).isFalse();
            } else {
                Pattern.compile(c.get("expectPolicy").asText());
                if (c.has("expectPolicy2")) {
                    Pattern.compile(c.get("expectPolicy2").asText());
                }
            }
        }
        assertThat(ids).hasSize(38);
        assertThat(refuse).isEqualTo(4);
    }
}
