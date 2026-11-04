package kr.fintarget.api.domain.realtime;

import kr.fintarget.api.domain.expense.event.ExpenseCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ExpenseRealtimeListener {

    static final String EXPENSE_CREATED = "expense-created";

    private final SseEmitterRegistry sseEmitterRegistry;

    // 지출이 DB에 커밋된 뒤에만 알린다. 앱이 이벤트를 받고 바로 다시 조회해도 새 지출이 보이게 하고, 롤백된 지출은 알리지 않는다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExpenseCreated(ExpenseCreatedEvent event) {
        sseEmitterRegistry.sendToUser(event.userId().toString(), EXPENSE_CREATED, Map.of("expenses", event.expenses()));
    }
}
