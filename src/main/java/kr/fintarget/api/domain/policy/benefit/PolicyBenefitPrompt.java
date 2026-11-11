package kr.fintarget.api.domain.policy.benefit;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 정책 혜택 추출 시스템 프롬프트 (#162). 본문은 src/main/resources/prompts/{VERSION}.md에 두고,
 * 바꿀 때는 새 버전 파일을 추가한 뒤 VERSION을 올린다 (평가셋으로 버전끼리 비교하려고 이전 파일은 남긴다).
 * 정책 원문은 시스템 프롬프트가 아니라 사용자 메시지로 보낸다 — 원문 속 문장이 규칙처럼 읽히지 않게 하려는 것.
 */
@Component
public class PolicyBenefitPrompt {

    public static final String VERSION = "policy-benefit-v1";

    private static final Pattern VERSION_PATTERN = Pattern.compile("policy-benefit-v\\d+");

    private final String version;
    private final String instructions;

    public PolicyBenefitPrompt() {
        this(VERSION, load(VERSION));
    }

    private PolicyBenefitPrompt(String version, String instructions) {
        this.version = version;
        this.instructions = instructions;
    }

    /** 지정한 버전의 프롬프트를 읽는다. 버전 비교 평가용이며, 운영 경로는 기본 생성자(VERSION)만 쓴다 */
    public static PolicyBenefitPrompt ofVersion(String version) {
        if (version == null || !VERSION_PATTERN.matcher(version).matches()) {
            throw new IllegalArgumentException("프롬프트 버전 형식이 아닙니다: " + version);
        }
        return new PolicyBenefitPrompt(version, load(version));
    }

    public String version() {
        return version;
    }

    public String instructions() {
        return instructions;
    }

    public String userMessage(BenefitSource source) {
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
