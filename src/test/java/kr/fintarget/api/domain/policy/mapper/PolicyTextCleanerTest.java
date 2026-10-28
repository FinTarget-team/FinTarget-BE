package kr.fintarget.api.domain.policy.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyTextCleanerTest {

    @Test
    void HTML_엔티티를_디코딩한다() {
        assertThat(PolicyTextCleaner.clean("근로장려금 신청 대상&#40;수급자&#41;인 자영업자"))
                .isEqualTo("근로장려금 신청 대상(수급자)인 자영업자");
        assertThat(PolicyTextCleaner.clean("A &amp; B &lt;C&gt;")).isEqualTo("A & B <C>");
    }

    @Test
    void 줄바꿈을_통일하고_줄마다_앞뒤_공백을_제거한다() {
        assertThat(PolicyTextCleaner.clean("  1) 상담  \r\n  2) 교육\r3) 지원  "))
                .isEqualTo("1) 상담\n2) 교육\n3) 지원");
    }

    @Test
    void 연속된_빈_줄은_하나로_줄이고_한_줄_띄움은_유지한다() {
        assertThat(PolicyTextCleaner.clean("첫째\n\n\n\n둘째\n\n셋째"))
                .isEqualTo("첫째\n\n둘째\n\n셋째");
        assertThat(PolicyTextCleaner.clean("첫째\n  \n \n둘째")).isEqualTo("첫째\n\n둘째");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "-", " - ", "\n-\n", "\r\n"})
    void 빈_값과_하이픈만_있으면_null이다(String raw) {
        assertThat(PolicyTextCleaner.clean(raw)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"없음", "해당 없음", "해당없음", "--", "-없음"})
    void 없음_해당_없음_등은_원문을_유지한다(String raw) {
        assertThat(PolicyTextCleaner.clean(raw)).isEqualTo(raw);
    }

    @Test
    void unescapeOnly는_엔티티만_디코딩하고_공백과_줄바꿈은_그대로_둔다() {
        assertThat(PolicyTextCleaner.unescapeOnly("  대상&#40;청년&#41;\r\n\n\n끝  "))
                .isEqualTo("  대상(청년)\r\n\n\n끝  ");
        assertThat(PolicyTextCleaner.unescapeOnly("-")).isEqualTo("-");
        assertThat(PolicyTextCleaner.unescapeOnly(null)).isNull();
    }

    @Test
    void labeledLines는_값이_빈_줄을_통째로_뺀다() {
        assertThat(PolicyTextCleaner.labeledLines(
                "대출 한도", "3500만원",
                "금리", "  ",
                "상환 방법", "-",
                "기간", null,
                "용도", "생계&#40;긴급&#41;"))
                .isEqualTo("대출 한도: 3500만원\n용도: 생계(긴급)");
    }

    @Test
    void labeledLines는_모든_값이_비면_null이다() {
        assertThat(PolicyTextCleaner.labeledLines("대상", null, "신용 조건", "-")).isNull();
    }

    @Test
    void labeledLines는_라벨과_값이_쌍이_아니면_예외가_발생한다() {
        assertThatThrownBy(() -> PolicyTextCleaner.labeledLines("라벨만"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
