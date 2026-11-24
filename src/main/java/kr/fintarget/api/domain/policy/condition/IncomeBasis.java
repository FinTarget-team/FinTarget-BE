package kr.fintarget.api.domain.policy.condition;

/**
 * 정책 소득 조건의 기준 (#220). 정책 원문에서 AI로 추출한다.
 * 추천 제외 판정은 {@link #INDIVIDUAL}이고 상한 금액이 있을 때만 한다 ({@link PolicyConditionMatcher}).
 */
public enum IncomeBasis {

    /** 본인(개인) 소득 기준이 명확함. 상한 금액(incomeMaxMonthly)과 함께 쓴다 */
    INDIVIDUAL,

    /** 부부합산 소득 기준 */
    COUPLE_COMBINED,

    /** 기준 중위소득 비율 (가구 소득 기준) */
    MEDIAN_RATIO,

    /** 기초생활수급자·차상위 등 자격 기준 */
    RECIPIENT,

    /** 소득 조건 없음 */
    NONE,

    /** 원문만으로 알 수 없거나 조건이 여러 개라 해석이 애매함 */
    UNKNOWN
}
