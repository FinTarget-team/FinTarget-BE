package kr.fintarget.api.domain.expense.event;

import kr.fintarget.api.domain.expense.dto.ExpenseResponse;

import java.util.List;
import java.util.UUID;

/**
 * 은행 웹훅으로 한 사용자의 지출이 새로 저장됐음을 알리는 이벤트. 웹훅 요청 하나에서 사용자별로 한 번 발행한다.
 */
public record ExpenseCreatedEvent(UUID userId, List<ExpenseResponse> expenses) {
}
