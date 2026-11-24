package kr.fintarget.api.domain.policy.recommend.rerank;

/**
 * 리랭킹 응답을 해석할 수 없음 (#229). 메시지에는 응답 원문을 넣지 않는다.
 */
public class PolicyRerankParseException extends RuntimeException {

    public PolicyRerankParseException(String message) {
        super(message);
    }
}
