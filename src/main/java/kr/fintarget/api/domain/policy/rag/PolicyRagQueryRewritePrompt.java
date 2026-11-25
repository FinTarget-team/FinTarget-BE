package kr.fintarget.api.domain.policy.rag;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * 통합 답변 검색 문장 바꿔 쓰기 시스템 프롬프트 (#241). 관리 방식은 {@link PolicyRagPrompt}와 같다:
 * 본문은 src/main/resources/prompts/{VERSION}.md에 두고, 바꿀 때는 새 버전 파일을 추가한 뒤 VERSION을 올린다.
 * 질문은 시스템 프롬프트가 아니라 사용자 메시지로 보낸다 (질문 속 지시가 규칙처럼 읽히지 않게).
 */
@Component
public class PolicyRagQueryRewritePrompt {

    public static final String VERSION = "policy-rag-query-rewrite-v1";

    private final String instructions;

    public PolicyRagQueryRewritePrompt() {
        this.instructions = load(VERSION);
    }

    public String version() {
        return VERSION;
    }

    public String instructions() {
        return instructions;
    }

    private static String load(String version) {
        String path = "prompts/" + version + ".md";
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 파일을 읽을 수 없습니다: " + path, e);
        }
    }
}
