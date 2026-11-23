package kr.fintarget.api.domain.policy.condition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionExtractionParserTest {

    @Test
    void 정상_응답을_읽고_연소득_상한은_12로_나눈_월_금액으로_바꾼다() {
        ConditionExtraction result = ConditionExtractionParser.parse("""
                {"incomeBasis": "INDIVIDUAL", "incomeMaxAmount": 50000000, "incomePeriod": "ANNUAL",
                 "housingRequirement": "HOMELESS_REQUIRED", "targetType": "INDIVIDUAL", "extractedRegions": ["JEONNAM"]}
                """);

        assertThat(result.incomeBasis()).isEqualTo(IncomeBasis.INDIVIDUAL);
        assertThat(result.incomeMaxMonthly()).isEqualTo(4_166_666L);
        assertThat(result.housingRequirement()).isEqualTo(HousingRequirement.HOMELESS_REQUIRED);
        assertThat(result.targetType()).isEqualTo(TargetType.INDIVIDUAL);
        assertThat(result.regions()).containsExactly("JEONNAM");
    }

    @Test
    void 월소득_상한은_그대로_쓰고_숫자_문자열도_읽는다() {
        ConditionExtraction result = ConditionExtractionParser.parse("""
                {"incomeBasis": "individual", "incomeMaxAmount": "2,500,000", "incomePeriod": "monthly",
                 "housingRequirement": "NONE", "targetType": "BOTH", "extractedRegions": []}
                """);

        assertThat(result.incomeBasis()).isEqualTo(IncomeBasis.INDIVIDUAL);
        assertThat(result.incomeMaxMonthly()).isEqualTo(2_500_000L);
        assertThat(result.targetType()).isEqualTo(TargetType.BOTH);
        assertThat(result.regions()).isEmpty();
    }

    @Test
    void 본인_소득이_아니면_상한_금액을_저장하지_않는다() {
        ConditionExtraction result = ConditionExtractionParser.parse("""
                {"incomeBasis": "COUPLE_COMBINED", "incomeMaxAmount": 50000000, "incomePeriod": "ANNUAL",
                 "housingRequirement": "NONE", "targetType": "INDIVIDUAL", "extractedRegions": []}
                """);

        assertThat(result.incomeBasis()).isEqualTo(IncomeBasis.COUPLE_COMBINED);
        assertThat(result.incomeMaxMonthly()).isNull();
    }

    @Test
    void 본인_소득인데_금액이나_기간을_해석할_수_없거나_범위를_벗어나면_소득_기준을_UNKNOWN으로_낮춘다() {
        assertThat(ConditionExtractionParser.parse(
                "{\"incomeBasis\": \"INDIVIDUAL\", \"incomeMaxAmount\": null, \"incomePeriod\": \"ANNUAL\"}").incomeBasis())
                .isEqualTo(IncomeBasis.UNKNOWN);
        assertThat(ConditionExtractionParser.parse(
                "{\"incomeBasis\": \"INDIVIDUAL\", \"incomeMaxAmount\": 50000000, \"incomePeriod\": \"WEEKLY\"}").incomeBasis())
                .isEqualTo(IncomeBasis.UNKNOWN);
        // 만원 단위로 잘못 답한 경우 (5000 = 5천만원을 만원 단위로) → 월 416원이라 범위 밖
        ConditionExtraction tooSmall = ConditionExtractionParser.parse(
                "{\"incomeBasis\": \"INDIVIDUAL\", \"incomeMaxAmount\": 5000, \"incomePeriod\": \"ANNUAL\"}");
        assertThat(tooSmall.incomeBasis()).isEqualTo(IncomeBasis.UNKNOWN);
        assertThat(tooSmall.incomeMaxMonthly()).isNull();
    }

    @Test
    void 정의되지_않은_enum_값과_누락된_필드는_그_필드만_UNKNOWN이다() {
        ConditionExtraction result = ConditionExtractionParser.parse("""
                {"incomeBasis": "HOUSEHOLD", "housingRequirement": "HOMELESS_REQUIRED"}
                """);

        assertThat(result.incomeBasis()).isEqualTo(IncomeBasis.UNKNOWN);
        assertThat(result.housingRequirement()).isEqualTo(HousingRequirement.HOMELESS_REQUIRED);
        assertThat(result.targetType()).isEqualTo(TargetType.UNKNOWN);
        assertThat(result.regions()).isEmpty();
    }

    @Test
    void 정의되지_않은_지역_코드가_하나라도_있으면_지역은_빈_값이다() {
        assertThat(ConditionExtractionParser.parse(
                "{\"extractedRegions\": [\"JEONNAM\", \"HWASUN\"]}").regions()).isEmpty();
        assertThat(ConditionExtractionParser.parse(
                "{\"extractedRegions\": \"JEONNAM\"}").regions()).isEmpty();
        assertThat(ConditionExtractionParser.parse(
                "{\"extractedRegions\": [\"jeonnam\", \"JEONNAM\", \"GWANGJU\"]}").regions())
                .containsExactly("JEONNAM", "GWANGJU");
    }

    @Test
    void 코드_블록이나_앞뒤_설명이_붙어도_JSON만_읽는다() {
        ConditionExtraction result = ConditionExtractionParser.parse("""
                결과입니다.
                ```json
                {"incomeBasis": "NONE", "housingRequirement": "NONE", "targetType": "BUSINESS", "extractedRegions": []}
                ```
                """);

        assertThat(result.incomeBasis()).isEqualTo(IncomeBasis.NONE);
        assertThat(result.targetType()).isEqualTo(TargetType.BUSINESS);
    }

    @Test
    void JSON_객체가_아니면_예외를_던진다() {
        assertThatThrownBy(() -> ConditionExtractionParser.parse(null)).isInstanceOf(ConditionExtractionException.class);
        assertThatThrownBy(() -> ConditionExtractionParser.parse("판단할 수 없습니다"))
                .isInstanceOf(ConditionExtractionException.class);
        assertThatThrownBy(() -> ConditionExtractionParser.parse("{\"incomeBasis\": }"))
                .isInstanceOf(ConditionExtractionException.class);
    }
}
