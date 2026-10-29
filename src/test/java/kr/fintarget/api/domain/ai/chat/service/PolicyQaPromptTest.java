package kr.fintarget.api.domain.ai.chat.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyQaPromptTest {

    private static final UserProfileSummary PROFILE =
            new UserProfileSummary("만 25세 또는 만 26세", "확인 필요", "서울", "무주택", "직장인");

    @Test
    void 클래스패스의_현재_버전_프롬프트를_읽어_원문과_사용자_정보를_채운다() {
        String rendered = new PolicyQaPrompt().render(new PolicyDocument("정책명: 청년 월세", false), PROFILE);

        assertThat(PolicyQaPrompt.VERSION).isEqualTo("policy-qa-v1");
        assertThat(rendered)
                .contains("[정책 원문]\n정책명: 청년 월세", "- 거주 지역: 서울", "정책 원문에서 확인할 수 없습니다")
                .doesNotContain("{{policy}}", "{{profile}}");
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
}
