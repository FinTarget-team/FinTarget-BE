package kr.fintarget.api.domain.policy.rag;

/**
 * 통합 정책 AI 답변 결과 상태 (#183).
 */
public enum PolicyRagStatus {

    /** 검색된 정책 원문을 근거로 답했다. sources에 근거 정책이 1건 이상 있다 */
    ANSWERED,

    /** 질문과 가까운 정책이 없거나, 모델 답변에 유효한 근거 번호가 없었다. 서버 고정 문구로 답한다 */
    NO_EVIDENCE,

    /** 나이 또는 소득 정보가 없어 맞춤 정책을 고를 수 없다. AI를 호출하지 않는다 */
    PROFILE_INCOMPLETE
}
