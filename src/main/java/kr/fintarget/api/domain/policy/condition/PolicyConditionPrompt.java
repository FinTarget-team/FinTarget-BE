package kr.fintarget.api.domain.policy.condition;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 정책 조건 추출 시스템 프롬프트 (#220). 관리 방식은 혜택 추출(PolicyBenefitPrompt, #162)과 같다:
 * 본문은 src/main/resources/prompts/{VERSION}.md에 두고, 바꿀 때는 새 버전 파일을 추가한 뒤 VERSION을 올린다.
 * 버전이 바뀌면 저장된 조건과 버전이 달라 모든 정책을 다시 추출한다.
 * 정책 원문은 시스템 프롬프트가 아니라 사용자 메시지로 보낸다 (원문 속 문장이 규칙처럼 읽히지 않게).
 */
@Component
public class PolicyConditionPrompt {

    public static final String VERSION = "policy-condition-v2";

    private static final Pattern VERSION_PATTERN = Pattern.compile("policy-condition-v\\d+");

    private final String version;
    private final String instructions;

    public PolicyConditionPrompt() {
        this(VERSION, load(VERSION));
    }

    private PolicyConditionPrompt(String version, String instructions) {
        this.version = version;
        this.instructions = instructions;
    }

    /** 지정한 버전의 프롬프트를 읽는다. 버전 비교용이며, 운영 경로는 기본 생성자(VERSION)만 쓴다 */
    public static PolicyConditionPrompt ofVersion(String version) {
        if (version == null || !VERSION_PATTERN.matcher(version).matches()) {
            throw new IllegalArgumentException("프롬프트 버전 형식이 아닙니다: " + version);
        }
        return new PolicyConditionPrompt(version, load(version));
    }

    public String version() {
        return version;
    }

    public String instructions() {
        return instructions;
    }

    public String userMessage(ConditionSource source) {
        return "[정책 원문]\n" + source.toPromptText();
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
