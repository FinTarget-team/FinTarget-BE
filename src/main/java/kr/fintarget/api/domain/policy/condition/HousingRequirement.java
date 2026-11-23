package kr.fintarget.api.domain.policy.condition;

/**
 * 정책의 주택 보유 조건 (#220). 무주택이 필수 자격일 때만 {@link #HOMELESS_REQUIRED}이며, 우대 조건일 뿐이면 {@link #NONE}이다.
 */
public enum HousingRequirement {

    /** 무주택이 필수 자격 (무주택자, 무주택 세대주 등) */
    HOMELESS_REQUIRED,

    /** 주택 보유 조건 없음 (무주택 우대만 있는 경우 포함) */
    NONE,

    /** 원문만으로 알 수 없음 */
    UNKNOWN
}
