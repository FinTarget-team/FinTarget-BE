package kr.fintarget.api.domain.simulation.estimate;

import kr.fintarget.api.domain.policy.benefit.BenefitType;
import kr.fintarget.api.domain.policy.entity.PolicyBenefit;

/**
 * 주거 정책 월 절감액 추정 결과 (#257).
 *
 * @param monthlyAmount 월 절감액(원)
 * @param months        절감 개월 수. 원문에 거주 기간이 없으면 null (계산은 기본 12개월)
 * @param basis         추정 근거 문구. 응답에 그대로 내려가므로 화면에서 "예상"임을 알 수 있게 쓴다
 */
public record SavingEstimateResult(long monthlyAmount, Integer months, String basis) {

    /**
     * 시뮬레이션 계산에 넣을 혜택 값. 메모리에서만 쓰고 DB에 저장하지 않으므로 추천 정렬에 영향이 없다.
     * 추정은 "최대 기준"이 아니라 평균 기준값에서 나온 값이라 maximum은 false다.
     */
    public PolicyBenefit toPolicyBenefit() {
        return PolicyBenefit.builder()
                .type(BenefitType.CASH)
                .monthlyAmount(monthlyAmount)
                .benefitMonths(months)
                .maximum(false)
                .evidence(basis)
                .build();
    }
}
