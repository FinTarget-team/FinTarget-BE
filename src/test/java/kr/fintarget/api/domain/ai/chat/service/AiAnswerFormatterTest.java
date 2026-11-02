package kr.fintarget.api.domain.ai.chat.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiAnswerFormatterTest {

    @Test
    void 이슈에서_확인된_마크다운_답변을_일반_텍스트로_바꾼다() {
        // #147: iOS 앱에서 확인된 답변 형태
        String answer = "네, 신청할 수 있습니다. 신청 기간은 **2026년 2월 6일부터 11월 20일까지**입니다. "
                + "[신청 링크](https://youth.seoul.go.kr/apply)에서 신청하세요.";

        assertThat(AiAnswerFormatter.format(answer))
                .isEqualTo("네, 신청할 수 있습니다. 신청 기간은 2026년 2월 6일부터 11월 20일까지입니다. 신청 링크에서 신청하세요.");
    }

    @Test
    void 강조_제목_코드_기호를_지운다() {
        assertThat(AiAnswerFormatter.format("## 신청 자격\n__무주택__ 청년, `만 19~34세`"))
                .isEqualTo("신청 자격\n무주택 청년, 만 19~34세");
    }

    @Test
    void 목록_기호는_하이픈으로_통일하고_번호_목록은_그대로_둔다() {
        assertThat(AiAnswerFormatter.format("결론입니다.\n* 첫째\n  • 둘째\n· 셋째\n- 넷째\n1. 신청서 작성\n2. 서류 제출"))
                .isEqualTo("결론입니다.\n- 첫째\n- 둘째\n- 셋째\n- 넷째\n1. 신청서 작성\n2. 서류 제출");
    }

    @Test
    void 본문의_URL을_지우고_빈_괄호와_URL만_있던_줄도_지운다() {
        String answer = "온라인으로 신청합니다(https://apply.example.com).\n신청 링크: https://apply.example.com\n"
                + "- www.example.go.kr\n문의처는 1588-0000입니다.";

        assertThat(AiAnswerFormatter.format(answer)).isEqualTo("온라인으로 신청합니다.\n문의처는 1588-0000입니다.");
    }

    @Test
    void URL이_없던_콜론_줄은_지우지_않는다() {
        assertThat(AiAnswerFormatter.format("다음 조건을 확인해야 합니다:\n- 무주택"))
                .isEqualTo("다음 조건을 확인해야 합니다:\n- 무주택");
    }

    @Test
    void 텍스트가_빈_링크는_통째로_지운다() {
        assertThat(AiAnswerFormatter.format("자세한 내용은 담당 기관에 문의하세요. [](https://example.com)"))
                .isEqualTo("자세한 내용은 담당 기관에 문의하세요.");
    }

    @Test
    void 한_개짜리_별표와_일반_문장은_그대로_두고_빈_줄은_하나로_줄인다() {
        assertThat(AiAnswerFormatter.format("  결론입니다.\r\n\r\n\r\n\r\n*예산 소진 시 조기 마감될 수 있습니다.  "))
                .isEqualTo("결론입니다.\n\n*예산 소진 시 조기 마감될 수 있습니다.");
        assertThat(AiAnswerFormatter.format(null)).isEmpty();
    }

    @Test
    void 원문용_URL_제거는_목록_기호와_마크다운처럼_보이는_원문을_그대로_둔다() {
        assertThat(AiAnswerFormatter.removeUrls("· 대상: 무주택 **청년**\n관련 사이트: https://www.example.go.kr"))
                .isEqualTo("· 대상: 무주택 **청년**");
        assertThat(AiAnswerFormatter.removeUrls("https://only-url.example.com")).isNull();
        assertThat(AiAnswerFormatter.removeUrls(null)).isNull();
    }
}
