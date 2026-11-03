package kr.fintarget.api.domain.policy.benefit;

/**
 * AI 응답을 혜택 추출 결과로 해석할 수 없을 때 (JSON 형식 오류, 알 수 없는 유형).
 * 호출 쪽은 이 정책의 추출만 건너뛰고 기존 값을 유지한다.
 */
public class BenefitExtractionException extends RuntimeException {

    public BenefitExtractionException(String message) {
        super(message);
    }

    public BenefitExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
