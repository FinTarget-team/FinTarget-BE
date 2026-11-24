package kr.fintarget.api.domain.ai.chat.service;

import kr.fintarget.api.domain.user.validation.BirthYearValidator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 정책 AI 질문 시스템 프롬프트. 본문은 src/main/resources/prompts/{VERSION}.md에 두고,
 * 바꿀 때는 파일을 새 버전으로 추가한 뒤 VERSION을 올린다 (#126 비교 평가를 위해 이전 버전 파일은 남겨둔다).
 * 사용 로그에 VERSION을 함께 남긴다.
 */
@Component
public class PolicyQaPrompt {

    public static final String VERSION = "policy-qa-v3";

    private static final String POLICY_PLACEHOLDER = "{{policy}}";
    private static final String PROFILE_PLACEHOLDER = "{{profile}}";
    private static final String TODAY_PLACEHOLDER = "{{today}}";
    private static final DateTimeFormatter TODAY_FORMAT = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN);
    private static final Pattern VERSION_PATTERN = Pattern.compile("policy-qa-v\\d+");

    private final String version;
    private final String template;

    public PolicyQaPrompt() {
        this(VERSION, load(VERSION));
    }

    PolicyQaPrompt(String template) {
        this(VERSION, template);
    }

    private PolicyQaPrompt(String version, String template) {
        if (!template.contains(POLICY_PLACEHOLDER) || !template.contains(PROFILE_PLACEHOLDER)) {
            throw new IllegalStateException("프롬프트 템플릿에 {{policy}}, {{profile}} 자리가 없습니다: " + version);
        }
        this.version = version;
        this.template = template;
    }

    /**
     * 지정한 버전의 프롬프트 파일을 읽는다. 버전별 답변 비교 평가(#126)용이며, 운영 경로는 기본 생성자(VERSION)만 쓴다.
     */
    public static PolicyQaPrompt ofVersion(String version) {
        if (version == null || !VERSION_PATTERN.matcher(version).matches()) {
            throw new IllegalArgumentException("프롬프트 버전 형식이 아닙니다: " + version);
        }
        return new PolicyQaPrompt(version, load(version));
    }

    public String version() {
        return version;
    }

    public String render(PolicyDocument document, UserProfileSummary profile) {
        return render(document, profile, LocalDate.now(BirthYearValidator.ZONE));
    }

    /**
     * @param today 오늘 날짜. "지금 신청할 수 있는지" 같은 질문을 신청 기간과 비교하도록 넣는다 (#216).
     *              {{today}} 자리가 없는 이전 버전 템플릿에는 들어가지 않는다
     */
    public String render(PolicyDocument document, UserProfileSummary profile, LocalDate today) {
        // 사용자 정보·날짜를 먼저 채운다: 정책 원문에 "{{profile}}" 같은 문자열이 있어도 치환되지 않도록
        return template
                .replace(TODAY_PLACEHOLDER, TODAY_FORMAT.format(today))
                .replace(PROFILE_PLACEHOLDER, profile.toQaPromptText())
                .replace(POLICY_PLACEHOLDER, document.text());
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
