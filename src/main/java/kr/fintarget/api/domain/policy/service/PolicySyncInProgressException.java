package kr.fintarget.api.domain.policy.service;

/**
 * 정책 동기화가 이미 실행 중일 때 새 동기화 요청을 거절하기 위한 예외. 409로 응답된다.
 */
public class PolicySyncInProgressException extends IllegalStateException {

    public PolicySyncInProgressException() {
        super("정책 동기화가 이미 실행 중입니다. 완료 후 다시 시도해주세요.");
    }
}
