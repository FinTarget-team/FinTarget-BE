package kr.fintarget.api.domain.policy.recommend.rerank;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyRerankParserTest {

    @Test
    void 정상_응답은_순서와_이유를_그대로_꺼낸다() {
        PolicyRerankParser.Selection selection = PolicyRerankParser.parse(
                "{\"selected\":[{\"policyId\":3,\"reason\":\" 월세 부담을 줄여 줍니다. \"},{\"policyId\":1,\"reason\":\"교통비 지원\"}]}", 5, 10);

        assertThat(selection.numbers()).containsExactly(3, 1);
        assertThat(selection.reasons()).containsExactly("월세 부담을 줄여 줍니다.", "교통비 지원");
        assertThat(selection.dropped()).isZero();
    }

    @Test
    void 코드_블록이나_앞뒤_문장이_있어도_JSON_객체만_읽는다() {
        PolicyRerankParser.Selection selection = PolicyRerankParser.parse(
                "결과입니다.\n```json\n{\"selected\":[{\"policyId\":\"2\",\"reason\":\"a\"},{\"policyId\":\"[4]\"}]}\n```", 5, 10);

        assertThat(selection.numbers()).containsExactly(2, 4);
        assertThat(selection.reasons()).containsExactly("a", "");
    }

    @Test
    void JSON이_아니거나_selected_배열이_없으면_해석_실패다() {
        assertThatThrownBy(() -> PolicyRerankParser.parse("1번과 3번을 추천합니다", 5, 10))
                .isInstanceOf(PolicyRerankParseException.class);
        assertThatThrownBy(() -> PolicyRerankParser.parse("{\"selected\": 3}", 5, 10))
                .isInstanceOf(PolicyRerankParseException.class);
        assertThatThrownBy(() -> PolicyRerankParser.parse("{\"items\":[]}", 5, 10))
                .isInstanceOf(PolicyRerankParseException.class);
        assertThatThrownBy(() -> PolicyRerankParser.parse("{\"selected\":[", 5, 10))
                .isInstanceOf(PolicyRerankParseException.class);
        assertThatThrownBy(() -> PolicyRerankParser.parse(null, 5, 10))
                .isInstanceOf(PolicyRerankParseException.class);
    }

    @Test
    void 후보_밖_번호_중복_형식_오류는_버리고_센다() {
        PolicyRerankParser.Selection selection = PolicyRerankParser.parse(
                "{\"selected\":[{\"policyId\":0},{\"policyId\":6},{\"policyId\":2},{\"policyId\":2},"
                        + "{\"policyId\":\"abc\"},{\"policyId\":1.5},\"3\",{\"reason\":\"번호 없음\"},{\"policyId\":5}]}", 5, 10);

        assertThat(selection.numbers()).containsExactly(2, 5);
        assertThat(selection.dropped()).isEqualTo(7);
    }

    @Test
    void 최대_N건까지만_쓴다() {
        PolicyRerankParser.Selection selection = PolicyRerankParser.parse(
                "{\"selected\":[{\"policyId\":1},{\"policyId\":2},{\"policyId\":3},{\"policyId\":4}]}", 5, 2);

        assertThat(selection.numbers()).containsExactly(1, 2);
        assertThat(selection.dropped()).isZero();
    }

    @Test
    void 빈_배열이면_0건이다() {
        assertThat(PolicyRerankParser.parse("{\"selected\":[]}", 5, 10).numbers()).isEmpty();
    }

    @Test
    void 이유가_길면_자른다() {
        String reason = "가".repeat(300);
        PolicyRerankParser.Selection selection = PolicyRerankParser.parse(
                "{\"selected\":[{\"policyId\":1,\"reason\":\"" + reason + "\"}]}", 5, 10);

        assertThat(selection.reasons().getFirst()).hasSize(PolicyRerankParser.REASON_MAX);
    }
}
