package kr.fintarget.api.domain.policy.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyCitationParserTest {

    @Test
    void 근거_번호를_처음_나온_순서로_꺼내고_본문에서_지운다() {
        PolicyCitationParser.Result result = PolicyCitationParser.parse(
                "월세 지원 정책은 2개입니다.\n- 청년 월세는 월 20만원을 지원합니다 [2]\n- 주거급여는 신청할 수 있습니다 [1].", 3);

        assertThat(result.cited()).containsExactly(2, 1);
        assertThat(result.invalidCount()).isZero();
        assertThat(result.text()).isEqualTo(
                "월세 지원 정책은 2개입니다.\n- 청년 월세는 월 20만원을 지원합니다\n- 주거급여는 신청할 수 있습니다.");
    }

    @Test
    void 쉼표_묶음과_연속_표기를_모두_읽고_중복은_한_번만_남긴다() {
        PolicyCitationParser.Result result = PolicyCitationParser.parse("두 정책 모두 해당합니다 [1, 3][3][1].", 3);

        assertThat(result.cited()).containsExactly(1, 3);
        assertThat(result.text()).isEqualTo("두 정책 모두 해당합니다.");
    }

    @Test
    void 검색_결과에_없는_번호는_버리고_센다() {
        PolicyCitationParser.Result result = PolicyCitationParser.parse("지원합니다 [0] [2] [6, 1].", 2);

        assertThat(result.cited()).containsExactly(2, 1);
        assertThat(result.invalidCount()).isEqualTo(2);
        assertThat(result.text()).isEqualTo("지원합니다.");
    }

    @Test
    void 유효하지_않은_번호만_있으면_근거가_없다() {
        PolicyCitationParser.Result result = PolicyCitationParser.parse("지원합니다 [7].", 5);

        assertThat(result.hasCitation()).isFalse();
        assertThat(result.invalidCount()).isEqualTo(1);
    }

    @Test
    void 번호가_없는_답변은_본문을_그대로_두고_근거가_없다() {
        PolicyCitationParser.Result result = PolicyCitationParser.parse("찾은 정책 원문에서 확인할 수 없습니다.", 5);

        assertThat(result.hasCitation()).isFalse();
        assertThat(result.invalidCount()).isZero();
        assertThat(result.text()).isEqualTo("찾은 정책 원문에서 확인할 수 없습니다.");
    }

    @Test
    void 번호_목록과_빈_대괄호는_근거로_보지_않는다() {
        PolicyCitationParser.Result result = PolicyCitationParser.parse("1. 신청서를 냅니다 []\n2. 심사를 받습니다 [1]", 1);

        assertThat(result.cited()).containsExactly(1);
        assertThat(result.text()).isEqualTo("1. 신청서를 냅니다 []\n2. 심사를 받습니다");
    }

    @Test
    void null이면_빈_결과() {
        PolicyCitationParser.Result result = PolicyCitationParser.parse(null, 5);

        assertThat(result.text()).isEmpty();
        assertThat(result.hasCitation()).isFalse();
    }
}
