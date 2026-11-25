package kr.fintarget.api.domain.policy.manual;

/**
 * 직접 등록 정책 동기화가 이미 실행 중일 때 (#245). IllegalStateException 핸들러가 409로 응답한다.
 */
public class ManualPolicySyncInProgressException extends IllegalStateException {

    public static final String MESSAGE = "직접 등록 정책 동기화가 이미 실행 중입니다. 완료 후 다시 시도해주세요.";

    public ManualPolicySyncInProgressException() {
        super(MESSAGE);
    }
}
