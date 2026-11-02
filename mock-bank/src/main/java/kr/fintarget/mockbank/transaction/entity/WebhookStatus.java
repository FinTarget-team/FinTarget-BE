package kr.fintarget.mockbank.transaction.entity;

public enum WebhookStatus {
    PENDING,    // 핀타겟으로 아직 보내지 않음
    SENT,       // 핀타겟이 새 지출로 저장함
    DUPLICATE,  // 핀타겟이 이미 있는 지출로 판단해 건너뜀
    FAILED      // 전송 실패
}
