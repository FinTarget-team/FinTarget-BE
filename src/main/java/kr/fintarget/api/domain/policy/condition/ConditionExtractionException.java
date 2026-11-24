package kr.fintarget.api.domain.policy.condition;

/**
 * AI 응답을 조건 추출 결과로 해석할 수 없을 때 (#220). JSON이 아니거나 객체가 아닌 경우다.
 * 호출 쪽은 해당 정책을 모든 조건 UNKNOWN으로 저장한다.
 */
public class ConditionExtractionException extends RuntimeException {

    public ConditionExtractionException(String message) {
        super(message);
    }

    public ConditionExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
