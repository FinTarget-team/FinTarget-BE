package kr.fintarget.api.domain.policy.benefit;

import kr.fintarget.api.domain.policy.entity.PolicyBenefit;

/**
 * 정책 원문에서 추출한 혜택 (#162). AI 응답을 {@link BenefitExtractionParser}가 검증한 결과다.
 *
 * @param type           혜택 유형
 * @param monthlyAmount  매달 받는 금액(원). CASH가 아니면 null
 * @param months         월 지급 기간(개월). 원문에 없으면 null (계산에서는 PolicyBenefit의 기본 기간 적용)
 * @param oneTimeAmount  한 번 받는 금액(원). CASH가 아니면 null
 * @param isMaximum      금액이 "최대·이내·한도"로 표현됐는지 (화면에 "최대 지원 금액 기준" 안내용)
 * @param evidence       판단 근거가 된 원문 문장
 * @param validationNote 검증 단계에서 결과를 바꿨다면 그 이유 (예: 근거 문장이 원문에 없어 UNKNOWN 처리). 바꾸지 않았으면 null
 */
public record BenefitExtraction(BenefitType type, Long monthlyAmount, Integer months, Long oneTimeAmount,
                                boolean isMaximum, String evidence, String validationNote) {

    public static BenefitExtraction of(BenefitType type, String evidence, String validationNote) {
        return new BenefitExtraction(type, null, null, null, false, evidence, validationNote);
    }

    /** 시뮬레이션에 반영할 혜택. CASH가 아니면 null */
    public PolicyBenefit toPolicyBenefit() {
        if (!type.reflectsInSimulation()) {
            return null;
        }
        return PolicyBenefit.builder()
                .monthlyAmount(monthlyAmount)
                .benefitMonths(monthlyAmount == null ? null : months)
                .oneTimeAmount(oneTimeAmount)
                .build();
    }
}
