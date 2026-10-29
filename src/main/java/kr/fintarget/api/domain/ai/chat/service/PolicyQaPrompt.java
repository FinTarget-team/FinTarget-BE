package kr.fintarget.api.domain.ai.chat.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * 정책 AI 질문 시스템 프롬프트. 본문은 src/main/resources/prompts/{VERSION}.md에 두고,
 * 바꿀 때는 파일을 새 버전으로 추가한 뒤 VERSION을 올린다 (#126 비교 평가를 위해 이전 버전 파일은 남겨둔다).
 * 사용 로그에 VERSION을 함께 남긴다.
 */
@Component
public class PolicyQaPrompt {

    public static final String VERSION = "policy-qa-v1";

    private static final String POLICY_PLACEHOLDER = "{{policy}}";
    private static final String PROFILE_PLACEHOLDER = "{{profile}}";

    private final String template;

    public PolicyQaPrompt() {
        this(load("prompts/" + VERSION + ".md"));
    }

    PolicyQaPrompt(String template) {
        if (!template.contains(POLICY_PLACEHOLDER) || !template.contains(PROFILE_PLACEHOLDER)) {
            throw new IllegalStateException("프롬프트 템플릿에 {{policy}}, {{profile}} 자리가 없습니다: " + VERSION);
        }
        this.template = template;
    }

    public String render(PolicyDocument document, UserProfileSummary profile) {
        // 사용자 정보를 먼저 채운다: 정책 원문에 "{{profile}}" 같은 문자열이 있어도 치환되지 않도록
        return template
                .replace(PROFILE_PLACEHOLDER, profile.toPromptText())
                .replace(POLICY_PLACEHOLDER, document.text());
    }

    private static String load(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 파일을 읽을 수 없습니다: " + path, e);
        }
    }
}
