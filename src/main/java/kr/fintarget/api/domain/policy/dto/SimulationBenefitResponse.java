package kr.fintarget.api.domain.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyBenefit;
import kr.fintarget.api.domain.simulation.estimate.SavingEstimateResult;

/**
 * 시뮬레이션 정책 반영에 쓰이는 현금 혜택 (#162). 정책 원문에서 AI로 추출한 값이다.
 *
 * <p>주거 정책은 실제 지급액이 없어도 공식 기준값으로 월 절감액을 추정해 내려줄 수 있다 (#257).
 * 이때 {@code estimated = true}이고 {@code basis}에 근거 문구가 있다. 추정값은 받는 금액이 아니라 "덜 쓰는 금액"이다.
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
        boolean maximum,
        @Schema(description = "주거비 절감 추정값인지 (#257). true면 실제 지급액이 아니라 공식 기준값으로 계산한 예상 절감액이므로, "
                + "화면에 \"예상 절감\"으로 표시하고 받는 금액처럼 쓰지 않습니다. 실제 현금성 지원이면 false입니다", example = "false")
        boolean estimated,
        @Schema(description = "추정 근거 문구 (#257). estimated가 true일 때만 값이 있고, false면 null입니다",
                example = "입주 시 서울 주요 대학가 원룸 평균 월세 62만원의 50% 수준 임대료로 계산한 예상 절감액",
                types = {"string", "null"})
        String basis
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
                benefit.isMaximum(),
                false,
                null);
    }

    /** 추정한 월 절감액 (#257). 원문에 거주 기간이 없으면 12개월로 계산한다 */
    public static SimulationBenefitResponse fromEstimate(SavingEstimateResult estimate) {
        return new SimulationBenefitResponse(
                estimate.monthlyAmount(),
                estimate.months() == null ? PolicyBenefit.DEFAULT_MONTHS_WHEN_UNSPECIFIED : estimate.months(),
                estimate.months() != null,
                null,
                false,
                true,
                estimate.basis());
    }
}
