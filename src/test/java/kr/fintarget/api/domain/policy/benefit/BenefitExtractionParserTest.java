package kr.fintarget.api.domain.policy.benefit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BenefitExtractionParserTest {

    private static final BenefitSource SOURCE = new BenefitSource("YOUTH_CENTER", "청년 월세 지원",
            "ㅇ (대상) 무주택 청년\nㅇ (주요내용) 월 최대 20만원씩 12개월 지원", null, null, null);

    @Test
    void CASH_응답을_금액과_기간으로_읽는다() {
        BenefitExtraction result = BenefitExtractionParser.parse("""
                {"type": "CASH", "monthlyAmount": 200000, "months": 12, "oneTimeAmount": null,
                 "isMaximum": true, "evidence": "월 최대 20만원씩 12개월 지원"}""", SOURCE);

        assertThat(result.type()).isEqualTo(BenefitType.CASH);
        assertThat(result.monthlyAmount()).isEqualTo(200_000L);
        assertThat(result.months()).isEqualTo(12);
        assertThat(result.oneTimeAmount()).isNull();
        assertThat(result.isMaximum()).isTrue();
        assertThat(result.validationNote()).isNull();
    }

    @Test
    void 코드_블록과_앞뒤_설명이_붙어도_JSON만_읽는다() {
        BenefitExtraction result = BenefitExtractionParser.parse("""
                결과입니다.
                ```json
                {"type": "CASH", "monthlyAmount": "200,000", "months": 12, "evidence": "월 최대 20만원씩 12개월 지원"}
                ```""", SOURCE);

        assertThat(result.type()).isEqualTo(BenefitType.CASH);
        assertThat(result.monthlyAmount()).isEqualTo(200_000L);
    }

    @Test
    void CASH가_아니면_금액을_버린다() {
        BenefitExtraction result = BenefitExtractionParser.parse("""
                {"type": "LOAN", "monthlyAmount": 100000, "months": 24, "oneTimeAmount": 3000000, "evidence": "이자 지원"}""",
                SOURCE);

        assertThat(result.type()).isEqualTo(BenefitType.LOAN);
        assertThat(result.monthlyAmount()).isNull();
        assertThat(result.months()).isNull();
        assertThat(result.oneTimeAmount()).isNull();
        assertThat(result.toPolicyBenefit("v", "h", null).hasAny()).isFalse();
    }

    @Test
    void 근거_문장이_원문에_없으면_UNKNOWN으로_낮춘다() {
        // 원문에 없는 금액을 지어낸 경우 시뮬레이션에 반영되지 않도록 한다
        BenefitExtraction result = BenefitExtractionParser.parse("""
                {"type": "CASH", "monthlyAmount": 500000, "months": 12, "evidence": "월 50만원 지원"}""", SOURCE);

        assertThat(result.type()).isEqualTo(BenefitType.UNKNOWN);
        assertThat(result.validationNote()).contains("근거");
        assertThat(result.toPolicyBenefit("v", "h", null).hasAny()).isFalse();
    }

    @Test
    void 근거_문장은_공백과_줄바꿈_차이를_무시하고_비교한다() {
        BenefitExtraction result = BenefitExtractionParser.parse("""
                {"type": "CASH", "monthlyAmount": 200000, "months": 12, "evidence": "월 최대 20만원씩  12개월\\n지원"}""",
                SOURCE);

        assertThat(result.type()).isEqualTo(BenefitType.CASH);
    }

    @Test
    void CASH인데_금액이_없으면_UNKNOWN으로_낮춘다() {
        BenefitExtraction result = BenefitExtractionParser.parse("""
                {"type": "CASH", "monthlyAmount": 0, "oneTimeAmount": null, "evidence": "월 최대 20만원씩 12개월 지원"}""",
                SOURCE);

        assertThat(result.type()).isEqualTo(BenefitType.UNKNOWN);
    }

    @Test
    void 금액이_상한을_넘으면_단위_착오로_보고_UNKNOWN으로_낮춘다() {
        // "20만원"을 만원 단위로 착각해 원 단위에 10000을 한 번 더 곱한 경우
        BenefitExtraction result = BenefitExtractionParser.parse("""
                {"type": "CASH", "monthlyAmount": 2000000000, "months": 12, "evidence": "월 최대 20만원씩 12개월 지원"}""",
                SOURCE);

        assertThat(result.type()).isEqualTo(BenefitType.UNKNOWN);
        assertThat(result.validationNote()).contains("상한");
    }

    @Test
    void 월_지급이_없으면_기간은_버린다() {
        BenefitSource source = new BenefitSource("YOUTH_CENTER", "결혼지원금", "결혼지원금 100만원", null, null, null);

        BenefitExtraction result = BenefitExtractionParser.parse("""
                {"type": "CASH", "oneTimeAmount": 1000000, "months": 12, "evidence": "결혼지원금 100만원"}""", source);

        assertThat(result.oneTimeAmount()).isEqualTo(1_000_000L);
        assertThat(result.months()).isNull();
    }

    @Test
    void JSON이_아니면_예외를_던진다() {
        assertThatThrownBy(() -> BenefitExtractionParser.parse("현금 지원 정책입니다.", SOURCE))
                .isInstanceOf(BenefitExtractionException.class);
    }

    @Test
    void 알_수_없는_유형이면_예외를_던진다() {
        assertThatThrownBy(() -> BenefitExtractionParser.parse("{\"type\": \"GRANT\"}", SOURCE))
                .isInstanceOf(BenefitExtractionException.class)
                .hasMessageContaining("GRANT");
    }
}
