package kr.fintarget.api.domain.policy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시뮬레이션에 반영할 현금 지원 혜택 (#163). 목표 달성 계산이 쓰는 최소 형식이며,
 * 이 값을 누가 채우는지(AI 추출 #162, 수동 정리)와는 무관하게 동작한다.
 * 모든 컬럼은 nullable이고, 혜택을 숫자로 환산할 수 없는 정책(대출·현물·서비스)은 전부 null이다.
 *
 * <p>기존 {@link Policy#getBenefitAmount()}는 소스가 주는 "연간 지원 금액" 한 값이라 지원 기간과
 * 1회 지급을 표현할 수 없었다. 계산은 이 클래스만 쓰고, benefitAmount는 목록 표시용으로 남긴다.
 */
@Embeddable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PolicyBenefit {

    /**
     * 원문에 지급 기간이 없는 월 지급을 반영하는 기간(개월) (#162). 기간이 없다고 목표 달성까지 계속 더하면
     * 실제로는 기간이 정해진 대부분의 정책에서 달성일이 비현실적으로 빨라져, 보수적으로 1년만 반영한다.
     * 추출 결과(benefitMonths)는 원문대로 null로 저장하고 상한은 계산에서만 적용해, 값을 바꿔도 재추출이 필요 없다
     */
    public static final int DEFAULT_MONTHS_WHEN_UNSPECIFIED = 12;

    // 매달 받는 금액(원). 지원 기간 동안만 더해진다
    @Column(name = "benefit_monthly_amount")
    private Long monthlyAmount;

    // 월 지급을 받는 기간(개월). null이면 원문에 기간이 없다는 뜻이며, 계산에는 DEFAULT_MONTHS_WHEN_UNSPECIFIED를 쓴다
    @Column(name = "benefit_months")
    private Integer benefitMonths;

    // 한 번만 받는 금액(원). 첫 달에 지급된다고 본다
    @Column(name = "benefit_one_time_amount")
    private Long oneTimeAmount;

    /** 계산에 반영할 금액이 하나라도 있는지. 없으면 정책 반영 결과를 내지 않는다 */
    public boolean hasAny() {
        return positive(monthlyAmount) || positive(oneTimeAmount);
    }

    /** n번째 달(1부터)에 지원금으로 더해지는 금액. 지원 기간이 끝난 달에는 0이다 */
    public long amountForMonth(int month) {
        long amount = 0L;
        if (positive(monthlyAmount) && month <= effectiveMonths()) {
            amount += monthlyAmount;
        }
        if (positive(oneTimeAmount) && month == 1) {
            amount += oneTimeAmount;
        }
        return amount;
    }

    /** 계산에 쓰는 월 지급 기간. 원문에 기간이 없으면(null) DEFAULT_MONTHS_WHEN_UNSPECIFIED */
    public int effectiveMonths() {
        return benefitMonths != null ? benefitMonths : DEFAULT_MONTHS_WHEN_UNSPECIFIED;
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }
}
