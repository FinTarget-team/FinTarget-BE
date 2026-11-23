package kr.fintarget.api.domain.ai.chat.service;

import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyQaPromptTest {

    private static final UserProfileSummary PROFILE =
            new UserProfileSummary("만 25세 또는 만 26세", "확인 필요", "서울", "무주택", "직장인");

    @Test
    void 클래스패스의_현재_버전_프롬프트를_읽어_원문과_사용자_정보를_채운다() {
        String rendered = new PolicyQaPrompt().render(new PolicyDocument("정책명: 청년 월세", false), PROFILE);

        assertThat(PolicyQaPrompt.VERSION).isEqualTo("policy-qa-v3");
        assertThat(rendered)
                .contains("[정책 원문]\n정책명: 청년 월세", "- 거주 지역: 서울", "정책 원문에서 확인할 수 없습니다")
                .doesNotContain("{{policy}}", "{{profile}}");
    }

    @Test
    void v3는_소득_환산_비교와_묻지_않은_자격_금지_원문_값_합치기_규칙을_담는다() {
        String v3 = new PolicyQaPrompt().render(new PolicyDocument("정책명: 청년 월세", false), PROFILE);

        assertThat(v3).contains("\"연소득 환산\" 구간과 비교합니다", "자격 해당 여부(지역, 나이, 소득 등)를 덧붙이지 않습니다",
                "함께 성립할 수 있는 내용은 합쳐서", "\"네\", \"아니요\"로 답을 정하지 않습니다", "항목 뒤에 다른 문단을 붙이지 않습니다", "결론과 항목 사이에 빈 줄을 넣지 않습니다",
                "링크와 URL은 답변에 쓰지 않습니다", "항목은 최대 3개", "마크다운 문법을 쓰지 않습니다");
    }

    @Test
    void v3는_오늘_날짜와_중위소득_비율을_넣고_기간_계산_대화_정보_규칙을_담는다() {
        String v3 = new PolicyQaPrompt().render(new PolicyDocument("정책명: 청년 월세", false), PROFILE,
                LocalDate.of(2026, 11, 23));

        assertThat(v3).contains("[오늘 날짜]\n2026년 11월 23일 (월)", "- 1인 가구 기준 중위소득 대비: 확인 필요",
                        "\"1인 가구 기준으로 보면\"이라고 밝히고", "사업 기간을 신청 기간이라고 바꿔 말하지 않습니다",
                        "[오늘 날짜]와 원문의 신청 기간을 비교해", "원문 숫자만으로 바로 구할 수 있는 합계는 계산해서",
                        "사용자가 대화에서 직접 알려준 사실")
                .doesNotContain("{{today}}");
    }

    @Test
    void 이전_버전은_비교_평가용으로_계속_읽힌다() {
        String v2 = PolicyQaPrompt.ofVersion("policy-qa-v2").render(new PolicyDocument("정책명: 청년 월세", false), PROFILE);
        String v1 = PolicyQaPrompt.ofVersion("policy-qa-v1").render(new PolicyDocument("정책명: 청년 월세", false), PROFILE);

        assertThat(v2).contains("사용자가 물은 것에만 답합니다", "항목은 최대 3개").doesNotContain("연소득 환산", "[오늘 날짜]");
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
