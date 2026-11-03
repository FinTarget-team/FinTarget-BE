package kr.fintarget.api.domain.policy.benefit;

/**
 * 혜택 추출이 이미 실행 중일 때 (동기화 후 추출과 관리자 백필이 겹친 경우). IllegalStateException 핸들러가 409로 응답한다.
 */
public class BenefitExtractionInProgressException extends IllegalStateException {

    public static final String MESSAGE = "정책 혜택 추출이 이미 실행 중입니다. 완료 후 다시 시도해주세요.";

    public BenefitExtractionInProgressException() {
        super(MESSAGE);
    }
}
