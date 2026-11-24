package kr.fintarget.api.domain.policy.recommend.rerank;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 추천 리랭킹 시스템 프롬프트 (#229). 관리 방식은 조건 추출(PolicyConditionPrompt)과 같다:
 * 본문은 src/main/resources/prompts/{VERSION}.md에 두고, 바꿀 때는 새 버전 파일을 추가한 뒤 VERSION을 올린다.
 * 버전은 캐시 입력 해시에 들어가므로 올리면 모든 사용자의 리랭킹 결과를 다시 계산한다.
 * 사용자 정보와 후보 정책은 시스템 프롬프트가 아니라 사용자 메시지로 보낸다 (정책 문구가 규칙처럼 읽히지 않게).
 */
@Component
public class PolicyRerankPrompt {

    public static final String VERSION = "policy-rerank-v1";

    private final String template;

    public PolicyRerankPrompt() {
        this.template = load(VERSION);
    }

    public String version() {
        return VERSION;
    }

    /** 고를 건수(N)를 채운 시스템 프롬프트 */
    public String instructions(int limit) {
        return template.replace("{{limit}}", String.valueOf(limit));
    }

    public String userMessage(String profileText, List<String> candidateLines) {
        return "[사용자 정보]\n" + profileText + "\n\n[후보 정책] (" + candidateLines.size() + "건)\n"
                + String.join("\n", candidateLines);
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
