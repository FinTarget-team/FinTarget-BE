package kr.fintarget.api.domain.policy.recommend;

import kr.fintarget.api.domain.policy.entity.PolicyText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ApplyPeriodClassifierTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    private static ApplyPeriodStatus classify(String code, String period) {
        return ApplyPeriodClassifier.classify(PolicyText.builder().applyPeriodCode(code).applyPeriod(period).build(), TODAY);
    }

    @Test
    void 온통청년_신청기간_구분_코드로_상시와_마감을_판정한다() {
        assertThat(classify("0057002", null)).isEqualTo(ApplyPeriodStatus.OPEN);
        assertThat(classify("0057003", "20260101 ~ 20261231")).isEqualTo(ApplyPeriodStatus.NOT_OPEN);
        // 특정기간 코드는 기간 값으로 판정한다
        assertThat(classify("0057001", "20260101 ~ 20261231")).isEqualTo(ApplyPeriodStatus.OPEN);
        assertThat(classify("0057001", null)).isEqualTo(ApplyPeriodStatus.UNKNOWN);
    }

    @Test
    void 온통청년_기간은_여러_기간_중_하나라도_오늘을_포함하면_신청_가능이다() {
        assertThat(classify(null, "20260101 ~ 20261123")).isEqualTo(ApplyPeriodStatus.OPEN);
        assertThat(classify(null, "20261123 ~ 20261231")).isEqualTo(ApplyPeriodStatus.OPEN);
        assertThat(classify(null, "20260101 ~ 20261122")).isEqualTo(ApplyPeriodStatus.NOT_OPEN);
        assertThat(classify(null, "20270301 ~ 20270430")).isEqualTo(ApplyPeriodStatus.NOT_OPEN);
        assertThat(classify(null, "20260101 ~ 20260331\n20261101 ~ 20261130")).isEqualTo(ApplyPeriodStatus.OPEN);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "자금소진시까지 | OPEN",
            "한도 소진 시 까지 | OPEN",
            "연중 수시접수 | OPEN",
            "상시 | OPEN",
            "2026.1.15 ~ 자금소진시까지 | OPEN",
            "2026년 12월 1일 ~ 자금소진시 | NOT_OPEN",
            "2026-11-23 ~ 한도 소진 시 까지 | OPEN",
            "2025년 1월~12월 예산소진시까지 | NOT_OPEN",
            "2026년 1월~12월 예산소진시까지 | OPEN",
            "~ 2026. 12. 31. (또는 자금 소진시 까지) | OPEN",
            "~ 2026. 11. 22. (또는 자금 소진시 까지) | NOT_OPEN",
            "2026.3.~현재(예산 소진시 조기 종료) | OPEN",
            "기관 문의 | UNKNOWN",
            "2회(3,9월) | UNKNOWN",
    })
    void KINFA_신청_기간_형식을_판정한다(String period, ApplyPeriodStatus expected) {
        assertThat(classify(null, period)).isEqualTo(expected);
    }

    @Test
    void 신청_기간이_없으면_판정할_수_없다() {
        assertThat(classify(null, null)).isEqualTo(ApplyPeriodStatus.UNKNOWN);
        assertThat(classify(null, " ")).isEqualTo(ApplyPeriodStatus.UNKNOWN);
        assertThat(ApplyPeriodClassifier.classify((PolicyText) null, TODAY)).isEqualTo(ApplyPeriodStatus.UNKNOWN);
    }

    @Test
    void 사업_기간은_신청_기간으로_쓰지_않는다() {
        PolicyText businessOnly = PolicyText.builder().businessPeriod("20260101 ~ 20261231").build();

        assertThat(ApplyPeriodClassifier.classify(businessOnly, TODAY)).isEqualTo(ApplyPeriodStatus.UNKNOWN);
    }
}
