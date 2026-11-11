package kr.fintarget.api.domain.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyBenefit;

/**
 * 시뮬레이션 정책 반영에 쓰이는 현금 혜택 (#162). 정책 원문에서 AI로 추출한 값이다.
 */
@Schema(description = "시뮬레이션 정책 반영 달성일 계산에 쓰이는 현금 혜택. 반영할 혜택이 없는 정책(대출·서비스·아직 추출 전)은 null")
public record SimulationBenefitResponse(
        @Schema(description = "매달 받는 금액(원). 월 지급이 없으면 null", example = "200000", types = {"integer", "null"})
        Long monthlyAmount,
        @Schema(description = "월 지급을 계산에 반영하는 개월 수. 원문에 기간이 없으면 12개월로 계산합니다. 월 지급이 없으면 null",
                example = "12", types = {"integer", "null"})
        Integer months,
        @Schema(description = "원문에 지급 기간이 적혀 있는지. false면 months는 기본값 12개월입니다", example = "true")
        boolean monthsSpecified,
        @Schema(description = "한 번 받는 금액(원). 첫 달에 받는 것으로 계산합니다. 없으면 null", example = "1000000", types = {"integer", "null"})
        Long oneTimeAmount,
        @Schema(description = "금액이 '최대·이내·한도' 기준인지. true면 화면에 \"최대 지원 금액 기준 예상\"을 안내해야 합니다", example = "true")
        boolean maximum
) {

    public static SimulationBenefitResponse from(Policy policy) {
        if (!policy.hasBenefitForSimulation()) {
            return null;
        }
        PolicyBenefit benefit = policy.getBenefit();
        boolean hasMonthly = benefit.getMonthlyAmount() != null && benefit.getMonthlyAmount() > 0;
        return new SimulationBenefitResponse(
                hasMonthly ? benefit.getMonthlyAmount() : null,
                hasMonthly ? benefit.effectiveMonths() : null,
                hasMonthly && benefit.getBenefitMonths() != null,
                benefit.getOneTimeAmount() != null && benefit.getOneTimeAmount() > 0 ? benefit.getOneTimeAmount() : null,
                benefit.isMaximum());
    }
}
