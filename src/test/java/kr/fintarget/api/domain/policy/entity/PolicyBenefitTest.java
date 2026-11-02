package kr.fintarget.api.domain.policy.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시뮬레이션에 반영할 혜택 금액 형식 (#163). 지원 기간·1회 지급 판정을 여기서 고정한다.
 */
class PolicyBenefitTest {

    private static PolicyBenefit benefit(Long monthlyAmount, Integer months, Long oneTimeAmount) {
        return PolicyBenefit.builder()
                .monthlyAmount(monthlyAmount)
                .benefitMonths(months)
                .oneTimeAmount(oneTimeAmount)
                .build();
    }

    @Test
    void 월_지급액이나_1회_지급액이_있으면_반영_대상이다() {
        assertThat(benefit(200_000L, 24, null).hasAny()).isTrue();
        assertThat(benefit(null, null, 3_000_000L).hasAny()).isTrue();
    }

    // 금액이 없거나 0인 정책은 반영하지 않는다. 기간만 있는 값(추출 실패로 금액이 비는 경우)도 제외
    @Test
    void 금액이_없거나_0이면_반영_대상이_아니다() {
        assertThat(benefit(null, null, null).hasAny()).isFalse();
        assertThat(benefit(0L, 24, 0L).hasAny()).isFalse();
        assertThat(benefit(null, 24, null).hasAny()).isFalse();
    }

    @Test
    void 월_지급액은_지원_기간_안에서만_더해진다() {
        PolicyBenefit benefit = benefit(200_000L, 24, null);

        assertThat(benefit.amountForMonth(1)).isEqualTo(200_000L);
        assertThat(benefit.amountForMonth(24)).isEqualTo(200_000L);
        assertThat(benefit.amountForMonth(25)).isZero();
    }

    @Test
    void 지원_기간이_null이면_계속_더해진다() {
        PolicyBenefit benefit = benefit(200_000L, null, null);

        assertThat(benefit.amountForMonth(1)).isEqualTo(200_000L);
        assertThat(benefit.amountForMonth(1_200)).isEqualTo(200_000L);
    }

    @Test
    void 한_번만_받는_금액은_첫_달에만_더해진다() {
        PolicyBenefit benefit = benefit(null, null, 3_000_000L);

        assertThat(benefit.amountForMonth(1)).isEqualTo(3_000_000L);
        assertThat(benefit.amountForMonth(2)).isZero();
    }

    @Test
    void 월_지급과_1회_지급이_함께_있으면_첫_달에는_합산된다() {
        PolicyBenefit benefit = benefit(200_000L, 24, 3_000_000L);

        assertThat(benefit.amountForMonth(1)).isEqualTo(3_200_000L);
        assertThat(benefit.amountForMonth(2)).isEqualTo(200_000L);
        assertThat(benefit.amountForMonth(25)).isZero();
    }
}
