package kr.fintarget.mockbank.webhook.dto;

import java.util.List;
import java.util.UUID;

/**
 * 메인 서버 공통 응답 형식 { status, message, data }. 성공 시 data는 거래별 처리 결과, 실패 시 null
 */
public record WebhookResponse(int status, String message, List<Item> data) {

    public record Item(UUID transactionId, String result) {
    }
}
