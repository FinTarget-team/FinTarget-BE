package kr.fintarget.api.domain.policy.rag;

import kr.fintarget.api.domain.ai.chat.service.UserProfileSummary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 통합 정책 AI 답변 시스템 프롬프트 (#183). 관리 방식은 {@link kr.fintarget.api.domain.ai.chat.service.PolicyQaPrompt}와 같다:
 * 본문은 src/main/resources/prompts/{VERSION}.md에 두고, 바꿀 때는 새 버전 파일을 추가한 뒤 VERSION을 올린다.
 *
 * <p>검색된 청크는 목록 순서대로 [1], [2] 번호를 붙여 넣는다. 모델이 답변에 적은 번호는
 * {@link PolicyCitationParser}가 같은 순서로 정책에 되돌린다.
 */
@Component
public class PolicyRagPrompt {

    public static final String VERSION = "policy-rag-v1";

    private static final String CHUNKS_PLACEHOLDER = "{{chunks}}";
    private static final String PROFILE_PLACEHOLDER = "{{profile}}";
    private static final Pattern VERSION_PATTERN = Pattern.compile("policy-rag-v\\d+");

    private final String version;
    private final String template;

    public PolicyRagPrompt() {
        this(VERSION, load(VERSION));
    }

    PolicyRagPrompt(String template) {
        this(VERSION, template);
    }

    private PolicyRagPrompt(String version, String template) {
        if (!template.contains(CHUNKS_PLACEHOLDER) || !template.contains(PROFILE_PLACEHOLDER)) {
            throw new IllegalStateException("프롬프트 템플릿에 {{chunks}}, {{profile}} 자리가 없습니다: " + version);
        }
        this.version = version;
        this.template = template;
    }

    /**
     * 지정한 버전의 프롬프트 파일을 읽는다. 버전별 비교 평가용이며, 운영 경로는 기본 생성자(VERSION)만 쓴다.
     */
    public static PolicyRagPrompt ofVersion(String version) {
        if (version == null || !VERSION_PATTERN.matcher(version).matches()) {
            throw new IllegalArgumentException("프롬프트 버전 형식이 아닙니다: " + version);
        }
        return new PolicyRagPrompt(version, load(version));
    }

    public String version() {
        return version;
    }

    /**
     * @param chunks 검색된 청크 본문. 목록 순서가 근거 번호다 (첫 번째 = [1])
     */
    public String render(List<String> chunks, UserProfileSummary profile) {
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("프롬프트에 넣을 청크가 없습니다.");
        }
        // 사용자 정보를 먼저 채운다: 청크 본문에 "{{profile}}" 같은 문자열이 있어도 치환되지 않도록
        return template
                .replace(PROFILE_PLACEHOLDER, profile.toPromptText())
                .replace(CHUNKS_PLACEHOLDER, numbered(chunks));
    }

    static String numbered(List<String> chunks) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            if (i > 0) {
                text.append("\n\n");
            }
            text.append('[').append(i + 1).append("]\n").append(chunks.get(i).strip());
        }
        return text.toString();
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
