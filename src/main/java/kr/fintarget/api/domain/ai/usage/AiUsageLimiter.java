package kr.fintarget.api.domain.ai.usage;

import kr.fintarget.api.domain.user.validation.BirthYearValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 사용자별·기능별 일일 AI 호출 한도 (#183). 날짜는 KST 기준이라 자정(KST)에 다시 채워진다.
 *
 * <p>한도가 0 이하면 무제한이다 (#239 부스 시연). 이때는 사용량을 세지 않아 DB를 건드리지 않는다.
 * 호출 자체는 각 기능의 로그에 남으므로 시연 중 사용량은 로그로 확인한다.
 *
 * <p>각 단계는 별도 트랜잭션(REQUIRES_NEW)으로 바로 커밋한다. 호출하는 쪽 트랜잭션(읽기 전용일 수 있음)과 섞이지 않고,
 * AI 호출 중에 행 잠금을 쥐고 있지 않기 위해서다.
 *
 * <p>첫 호출은 행이 없어 INSERT한다. 같은 사용자가 동시에 첫 호출을 보내 INSERT가 유니크 제약에 걸리면
 * 다른 요청이 행을 만든 것이므로 조건부 UPDATE를 한 번 더 시도한다.
 */
@Slf4j
@Component
public class AiUsageLimiter {

    private final AiDailyUsageRepository repository;
    private final TransactionTemplate tx;
    private final Clock clock;

    @Autowired
    public AiUsageLimiter(AiDailyUsageRepository repository, PlatformTransactionManager transactionManager) {
        this(repository, transactionManager, Clock.system(BirthYearValidator.ZONE));
    }

    AiUsageLimiter(AiDailyUsageRepository repository, PlatformTransactionManager transactionManager, Clock clock) {
        this.repository = repository;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /** 한도가 0 이하면 무제한 (#239). 호출하는 쪽에서 설정값을 확인할 때도 쓴다 */
    public static boolean isUnlimited(int dailyLimit) {
        return dailyLimit < 1;
    }

    /**
     * 오늘 횟수를 1 차감한다. AI 호출이 실패하면 돌려받은 Ticket으로 {@link #release}를 호출해 되돌린다.
     * 한도가 0 이하면 차감하지 않고 바로 통과시킨다 (#239).
     *
     * @throws AiUsageLimitExceededException 오늘 한도를 모두 썼을 때 (429)
     */
    public Ticket acquire(String userId, AiUsageFeature feature, int dailyLimit) {
        if (isUnlimited(dailyLimit)) {
            return Ticket.unlimited(userId, feature);
        }
        LocalDate today = LocalDate.now(clock);
        if (increment(userId, today, feature, dailyLimit)) {
            return new Ticket(userId, today, feature);
        }
        if (exists(userId, today, feature)) {
            throw new AiUsageLimitExceededException(feature);
        }
        try {
            tx.executeWithoutResult(status -> repository.saveAndFlush(new AiDailyUsage(userId, today, feature, 1)));
            return new Ticket(userId, today, feature);
        } catch (DataIntegrityViolationException e) {
            // 동시에 들어온 다른 요청이 오늘 행을 먼저 만들었다
            if (increment(userId, today, feature, dailyLimit)) {
                return new Ticket(userId, today, feature);
            }
            throw new AiUsageLimitExceededException(feature);
        }
    }

    /**
     * acquire로 차감한 1회를 되돌린다. 차감한 날짜 기준이라 자정을 넘겨 실패해도 그 날 횟수가 돌아간다.
     * 되돌리기가 실패해도 원래 예외를 가리지 않도록 로그만 남긴다. 무제한 티켓은 되돌릴 것이 없다 (#239).
     */
    public void release(Ticket ticket) {
        if (ticket.unlimited()) {
            return;
        }
        try {
            tx.executeWithoutResult(status -> repository.decrement(ticket.userId(), ticket.date(), ticket.feature()));
        } catch (RuntimeException e) {
            log.warn("AI 일일 사용 횟수 되돌리기 실패 feature={} date={}", ticket.feature(), ticket.date(), e);
        }
    }

    private boolean increment(String userId, LocalDate date, AiUsageFeature feature, int limit) {
        Integer updated = tx.execute(status -> repository.incrementIfBelow(userId, date, feature, limit));
        return updated != null && updated > 0;
    }

    private boolean exists(String userId, LocalDate date, AiUsageFeature feature) {
        return Boolean.TRUE.equals(tx.execute(status ->
                repository.findByUserIdAndUsageDateAndFeature(userId, date, feature).isPresent()));
    }

    /** @param date 차감한 날짜. 무제한이라 차감하지 않았으면 null (#239) */
    public record Ticket(String userId, LocalDate date, AiUsageFeature feature) {

        static Ticket unlimited(String userId, AiUsageFeature feature) {
            return new Ticket(userId, null, feature);
        }

        /** 차감하지 않은 티켓인지 (한도가 0 이하) */
        public boolean unlimited() {
            return date == null;
        }
    }
}
