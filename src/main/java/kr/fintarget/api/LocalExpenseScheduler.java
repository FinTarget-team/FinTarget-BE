package kr.fintarget.api;

import kr.fintarget.api.domain.expense.entity.Expense;
import kr.fintarget.api.domain.expense.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalExpenseScheduler {

    private final ExpenseRepository expenseRepository;

    private static final UUID PERSONA_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PERSONA_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private record AmountRange(long min, long max) {}

    private static final Map<String, AmountRange> CATEGORY_AMOUNT_RANGES = Map.of(
            "식비", new AmountRange(5_000, 30_000),
            "교통", new AmountRange(1_000, 5_000),
            "카페", new AmountRange(3_000, 10_000),
            "쇼핑", new AmountRange(10_000, 150_000),
            "문화생활", new AmountRange(10_000, 50_000),
            "통신", new AmountRange(30_000, 60_000),
            "의료", new AmountRange(5_000, 50_000),
            "구독", new AmountRange(5_000, 20_000)
    );

    private static final List<String> CATEGORIES = List.copyOf(CATEGORY_AMOUNT_RANGES.keySet());

    /**
     * 매일 오전 9시, 페르소나별 랜덤 소비 내역 1~5건 생성
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void generateRandomExpenses() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        for (UUID userId : List.of(PERSONA_1, PERSONA_2)) {
            int count = random.nextInt(1, 6);
            for (int i = 0; i < count; i++) {
                String category = CATEGORIES.get(random.nextInt(CATEGORIES.size()));
                AmountRange range = CATEGORY_AMOUNT_RANGES.get(category);
                long amount = random.nextLong(range.min(), range.max() + 1);

                expenseRepository.save(new Expense(userId, amount, category, category, today));
            }
        }
    }
}

