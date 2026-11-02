package kr.fintarget.api.domain.ai.chat.service;

import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyQaPromptTest {

    private static final UserProfileSummary PROFILE =
            new UserProfileSummary("만 25세 또는 만 26세", "확인 필요", "서울", "무주택", "직장인");

    @Test
    void 클래스패스의_현재_버전_프롬프트를_읽어_원문과_사용자_정보를_채운다() {
        String rendered = new PolicyQaPrompt().render(new PolicyDocument("정책명: 청년 월세", false), PROFILE);

        assertThat(PolicyQaPrompt.VERSION).isEqualTo("policy-qa-v2");
        assertThat(rendered)
                .contains("[정책 원문]\n정책명: 청년 월세", "- 거주 지역: 서울", "정책 원문에서 확인할 수 없습니다")
                .doesNotContain("{{policy}}", "{{profile}}");
    }

    @Test
    void v2는_답변_형식_규칙을_담고_v1은_비교_평가용으로_계속_읽힌다() {
        String v2 = new PolicyQaPrompt().render(new PolicyDocument("정책명: 청년 월세", false), PROFILE);
        String v1 = PolicyQaPrompt.ofVersion("policy-qa-v1").render(new PolicyDocument("정책명: 청년 월세", false), PROFILE);

        assertThat(v2).contains("링크와 URL은 답변에 쓰지 않습니다", "\"- \"로 시작하는 항목", "항목은 최대 4개",
                "마크다운 문법을 쓰지 않습니다", "사용자가 물은 것에만 답합니다");
        assertThat(v1).contains("신청 링크가 있으면 함께 안내합니다").doesNotContain("마크다운");
    }

    @Test
    void 정책_원문에_자리표시자_문자열이_있어도_다시_치환하지_않는다() {
        String rendered = new PolicyQaPrompt("A {{policy}} B {{profile}}")
                .render(new PolicyDocument("원문 {{profile}} 원문", false), PROFILE);

        assertThat(rendered).startsWith("A 원문 {{profile}} 원문 B - 나이:");
    }

    @Test
    void 자리표시자가_없는_템플릿은_거부한다() {
        assertThatThrownBy(() -> new PolicyQaPrompt("{{policy}} 만 있음")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 버전을_지정해_읽으면_기본_생성자와_같은_프롬프트를_만든다() {
        PolicyDocument document = new PolicyDocument("정책명: 청년 월세", false);

        PolicyQaPrompt prompt = PolicyQaPrompt.ofVersion(PolicyQaPrompt.VERSION);

        assertThat(prompt.version()).isEqualTo(PolicyQaPrompt.VERSION);
        assertThat(new PolicyQaPrompt().version()).isEqualTo(PolicyQaPrompt.VERSION);
        assertThat(prompt.render(document, PROFILE)).isEqualTo(new PolicyQaPrompt().render(document, PROFILE));
    }

    @Test
    void 버전_형식이_아니거나_파일이_없으면_거부한다() {
        assertThatThrownBy(() -> PolicyQaPrompt.ofVersion("../application")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PolicyQaPrompt.ofVersion(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PolicyQaPrompt.ofVersion("policy-qa-v999")).isInstanceOf(UncheckedIOException.class);
    }
}
