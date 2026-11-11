package kr.fintarget.api.domain.policy.benefit;

/**
 * 정책 혜택 유형 (#162). 시뮬레이션에 반영하는 것은 CASH뿐이다.
 * 분류 기준은 src/main/resources/prompts/policy-benefit-v1.md의 규칙과 같다.
 */
public enum BenefitType {
    // 현금·범용 지역화폐·상품권 정액 지급 (월 지급, 1회 지급, 둘 다 가능)
    CASH,
    // 본인 납입액에 지자체가 적립을 더해 주는 상품
    SAVINGS_MATCH,
    // 대출, 대출이자 지원, 보증료 환급, 채무 상환 지원
    LOAN,
    // 기업·사업주 대상 지원
    CORPORATE,
    // 현물, 이용권·바우처, 할인, 교육·상담, 실비 보전
    SERVICE,
    // 원문만으로 금액이나 배분을 알 수 없음
    UNKNOWN;

    public boolean reflectsInSimulation() {
        return this == CASH;
    }
}
