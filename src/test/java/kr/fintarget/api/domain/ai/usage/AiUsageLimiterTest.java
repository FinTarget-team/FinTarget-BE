package kr.fintarget.api.domain.ai.usage;

import kr.fintarget.api.domain.user.validation.BirthYearValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 일일 한도의 차감·초과·되돌리기·날짜 구분·동시 첫 호출 (#183, H2).
 * 리미터가 단계마다 REQUIRES_NEW로 커밋하므로 테스트 트랜잭션을 끄고, 끝나면 행을 지운다.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AiUsageLimiterTest {

    private static final String USER = "user-1";
    // 2026-11-05 23:30 KST
    private static final Instant LATE_NIGHT = Instant.parse("2026-11-05T14:30:00Z");

    @Autowired
    private AiDailyUsageRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private AiUsageLimiter limiter(Instant now) {
        return new AiUsageLimiter(repository, transactionManager, Clock.fixed(now, BirthYearValidator.ZONE));
    }

    private int used(String userId, LocalDate date, AiUsageFeature feature) {
        return repository.findByUserIdAndUsageDateAndFeature(userId, date, feature)
                .map(AiDailyUsage::getUsedCount).orElse(0);
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void 한도까지_차감하고_넘으면_429_예외() {
        AiUsageLimiter limiter = limiter(LATE_NIGHT);

        limiter.acquire(USER, AiUsageFeature.POLICY_RAG, 2);
        limiter.acquire(USER, AiUsageFeature.POLICY_RAG, 2);

        assertThatThrownBy(() -> limiter.acquire(USER, AiUsageFeature.POLICY_RAG, 2))
                .isInstanceOf(AiUsageLimitExceededException.class)
                .hasMessage(AiUsageFeature.POLICY_RAG.exceededMessage());
        assertThat(used(USER, LocalDate.of(2026, 11, 5), AiUsageFeature.POLICY_RAG)).isEqualTo(2);
    }

    @Test
    void 날짜는_KST_기준이고_다음_날에는_다시_채워진다() {
        limiter(LATE_NIGHT).acquire(USER, AiUsageFeature.POLICY_RAG, 1);
        assertThatThrownBy(() -> limiter(LATE_NIGHT).acquire(USER, AiUsageFeature.POLICY_RAG, 1))
                .isInstanceOf(AiUsageLimitExceededException.class);

        // 40분 뒤는 KST로 다음 날 00:10
        AiUsageLimiter.Ticket next = limiter(LATE_NIGHT.plusSeconds(40 * 60)).acquire(USER, AiUsageFeature.POLICY_RAG, 1);

        assertThat(next.date()).isEqualTo(LocalDate.of(2026, 11, 6));
    }

    @Test
    void 기능과_사용자별로_따로_센다() {
        AiUsageLimiter limiter = limiter(LATE_NIGHT);
        limiter.acquire(USER, AiUsageFeature.POLICY_RAG, 1);

        limiter.acquire(USER, AiUsageFeature.POLICY_SEARCH, 1);
        limiter.acquire("user-2", AiUsageFeature.POLICY_RAG, 1);

        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    void 되돌리면_한_번_더_쓸_수_있고_0_아래로_내려가지_않는다() {
        AiUsageLimiter limiter = limiter(LATE_NIGHT);
        AiUsageLimiter.Ticket ticket = limiter.acquire(USER, AiUsageFeature.POLICY_RAG, 1);

        limiter.release(ticket);
        limiter.acquire(USER, AiUsageFeature.POLICY_RAG, 1);
        limiter.release(ticket);
        limiter.release(ticket);

        assertThat(used(USER, ticket.date(), AiUsageFeature.POLICY_RAG)).isZero();
    }

    @Test
    void 차감한_날짜로_되돌리므로_자정을_넘겨_실패해도_그_날_횟수가_돌아간다() {
        AiUsageLimiter.Ticket ticket = limiter(LATE_NIGHT).acquire(USER, AiUsageFeature.POLICY_RAG, 5);

        limiter(LATE_NIGHT.plusSeconds(3600)).release(ticket);

        assertThat(used(USER, LocalDate.of(2026, 11, 5), AiUsageFeature.POLICY_RAG)).isZero();
    }

    @Test
    void 같은_사용자의_동시_첫_호출도_한도를_넘지_않는다() throws Exception {
        AiUsageLimiter limiter = limiter(LATE_NIGHT);
        int threads = 8;
        int limit = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Boolean>> calls = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            calls.add(() -> {
                try {
                    limiter.acquire(USER, AiUsageFeature.POLICY_RAG, limit);
                    return true;
                } catch (AiUsageLimitExceededException e) {
                    return false;
                }
            });
        }
        int succeeded = 0;
        try {
            for (Future<Boolean> result : executor.invokeAll(calls)) {
                if (result.get()) {
                    succeeded++;
                }
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(succeeded).isEqualTo(limit);
        assertThat(used(USER, LocalDate.of(2026, 11, 5), AiUsageFeature.POLICY_RAG)).isEqualTo(limit);
    }

    @Test
    void 한도가_1보다_작으면_거부한다() {
        assertThatThrownBy(() -> limiter(LATE_NIGHT).acquire(USER, AiUsageFeature.POLICY_RAG, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
