package kr.fintarget.api.domain.policy.condition;

/**
 * 정책이 개인에게 주는 경제적 혜택 유형 (#225). 정책 원문에서 AI로 추출하며, 추천 상위 N 선택에만 쓴다
 * ({@link kr.fintarget.api.domain.policy.recommend.RuleBasedPolicyRecommendationSelector}).
 *
 * <p>#162 혜택 유형(BenefitType)은 SERVICE가 바우처·할인·실비와 교육·상담·공간 제공을 함께 묶어, 경제적 혜택 여부를 나눌 수 없어 따로 둔다.
 */
public enum BenefitKind {

    /** 현금 지급, 적립 매칭, 이자 지원, 보증료 지원, 실비 환급 */
    MONEY,

    /** 바우처, 할인, 감면, 저렴한 임대, 무료 물품 */
    COST_SAVING,

    /** 일반 대출 상품 (이자 지원·보증료 지원은 MONEY) */
    LOAN,

    /** 상담, 교육, 강연, 행사, 캠프, 네트워킹, 공간 운영 */
    NON_FINANCIAL,

    /** 기관·단체 지원, 시설 조성 등 개인이 받는 혜택이 아닌 사업 */
    NOT_FOR_INDIVIDUAL,

    /** 원문만으로 알 수 없음 */
    UNKNOWN;

    /** 추천 상위 N의 1순위 후보 (현금·비용 절감 혜택) */
    public boolean isEconomicBenefit() {
        return this == MONEY || this == COST_SAVING;
    }
}
