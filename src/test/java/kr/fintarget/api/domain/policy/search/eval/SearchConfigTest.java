package kr.fintarget.api.domain.policy.search.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #184에서 비교하기로 한 조합 구성 (기준선 / 하한 상향 / KINFA 조건부 + 하한 상향).
 */
class SearchConfigTest {

    @Test
    void 비교_조합은_기준선과_하한_상향_셋과_KINFA_조건부_넷이다() {
        List<SearchConfig> configs = SearchConfig.comparisonSet();

        assertThat(configs).hasSize(8);
        assertThat(configs.getFirst()).isEqualTo(SearchConfig.PRODUCTION);
        // k와 배수는 운영값으로 고정하고 하한과 KINFA 조건부만 바꾼다
        assertThat(configs).allSatisfy(c -> {
            assertThat(c.k()).isEqualTo(SearchConfig.PRODUCTION.k());
            assertThat(c.chunkMultiplier()).isEqualTo(SearchConfig.PRODUCTION.chunkMultiplier());
        });
        assertThat(configs).filteredOn(c -> !c.excludeKinfaWithoutLoanIntent())
                .extracting(SearchConfig::maxDistance)
                .containsExactly(0.50, 0.60, 0.65, 0.70);
        // KINFA 조건부에 0.50을 넣어, 하한 상향과 KINFA 제외의 효과를 분리해 본다
        assertThat(configs).filteredOn(SearchConfig::excludeKinfaWithoutLoanIntent)
                .extracting(SearchConfig::maxDistance)
                .containsExactly(0.50, 0.60, 0.65, 0.70);
    }

    @Test
    void 기존_세_값_생성자는_KINFA_조건부가_꺼진_조합이다() {
        assertThat(new SearchConfig(5, 0.5, 3).excludeKinfaWithoutLoanIntent()).isFalse();
        assertThat(SearchConfig.PRODUCTION.excludeKinfaWithoutLoanIntent()).isFalse();
        assertThat(SearchConfig.WIDE.excludeKinfaWithoutLoanIntent()).isFalse();
    }

    @Test
    void KINFA_조건부는_대출_의도가_없는_질문에만_적용된다() {
        SearchConfig conditional = new SearchConfig(5, 0.6, 3, true);

        assertThat(conditional.excludesKinfaFor("자취하는데 월세가 너무 부담돼")).isTrue();
        assertThat(conditional.excludesKinfaFor("창업 자금 빌릴 수 있는 곳 있어?")).isFalse();
    }

    @Test
    void 조건부가_꺼진_조합은_어떤_질문에도_KINFA를_빼지_않는다() {
        assertThat(SearchConfig.PRODUCTION.excludesKinfaFor("자취하는데 월세가 너무 부담돼")).isFalse();
    }

    @Test
    void 라벨에_KINFA_조건부_여부가_드러난다() {
        assertThat(SearchConfig.PRODUCTION.label()).isEqualTo("k=5 d=0.50 m=3");
        assertThat(new SearchConfig(5, 0.65, 3, true).label()).isEqualTo("k=5 d=0.65 m=3 KINFA조건부");
    }
}
