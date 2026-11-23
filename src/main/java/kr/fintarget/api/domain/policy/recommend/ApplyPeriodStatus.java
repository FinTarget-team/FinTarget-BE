package kr.fintarget.api.domain.policy.recommend;

/**
 * 오늘 기준 신청 가능 여부 (#220). 추천 정렬의 첫 번째 기준이며, 순서가 곧 정렬 순위다.
 */
public enum ApplyPeriodStatus {

    /** 신청 기간 안이거나 상시·소진 시까지 */
    OPEN,

    /** 신청 기간을 판정할 수 없음 (빈 값, "기관 문의", 해석할 수 없는 형식) */
    UNKNOWN,

    /** 신청 기간이 끝났거나 아직 시작 전 (온통청년 마감 코드 포함) */
    NOT_OPEN
}
