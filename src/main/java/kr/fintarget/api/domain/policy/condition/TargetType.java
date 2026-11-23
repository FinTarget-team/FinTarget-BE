package kr.fintarget.api.domain.policy.condition;

/**
 * 정책 신청 대상 유형 (#220). 사업자 전용 정책을 개인 사용자 추천에서 빼는 데 쓴다.
 */
public enum TargetType {

    /** 개인만 신청 가능 */
    INDIVIDUAL,

    /** 소상공인·자영업자·법인·사업자만 신청 가능 */
    BUSINESS,

    /** 개인과 사업자 모두 신청 가능 */
    BOTH,

    /** 원문만으로 알 수 없음 */
    UNKNOWN
}
