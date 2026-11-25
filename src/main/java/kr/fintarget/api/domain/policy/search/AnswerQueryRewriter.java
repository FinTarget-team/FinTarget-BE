package kr.fintarget.api.domain.policy.search;

/**
 * 통합 답변 검색(#241)에서 원래 질문을 정책 검색용 문장으로 바꾼다.
 * 바꾼 문장은 검색에만 쓰고, 대출 의도·정책명·시·군·구 판정과 답변 생성에는 원래 질문을 쓴다.
 * 구현은 예외를 던지지 않는다 (실패·시간 초과면 {@link QueryRewrite}의 결과로 알린다).
 */
public interface AnswerQueryRewriter {

    /** false면 바꿔 쓰기를 호출하지 않는다 (policy.rag.query-rewrite.enabled) */
    boolean isEnabled();

    /** 원래 질문 검색의 가장 가까운 거리가 이 값 이하면 바꿔 쓰기를 생략한다. 그 거리 이하인 원래 결과는 합친 결과 앞에 고정한다 */
    double skipDistance();

    QueryRewrite rewrite(String question);
}
