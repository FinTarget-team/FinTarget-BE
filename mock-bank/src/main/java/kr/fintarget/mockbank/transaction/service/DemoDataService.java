package kr.fintarget.mockbank.transaction.service;

import kr.fintarget.mockbank.transaction.ExpenseCategory;
import kr.fintarget.mockbank.transaction.entity.Transaction;
import kr.fintarget.mockbank.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * 시연 버튼용 거래 생성.
 * 메인 서버는 사용자별 (카테고리, 금액, 날짜, 설명) 조합으로 중복을 판정하므로,
 * 생성하는 거래는 같은 사용자의 기존 거래·이번에 만든 거래와 이 조합이 겹치지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class DemoDataService {

    public static final int BASELINE_MONTHS = 3;
    public static final int RANDOM_COUNT = 10;
    // 메인 서버 넛지 기준(평소의 1.5배)보다 넉넉하게 2배로 만든다
    public static final int SURGE_MULTIPLIER = 2;
    private static final long SURGE_MIN_AMOUNT = 20_000;
    private static final long SURGE_MAX_AMOUNT = 60_000;
    private static final List<String> SURGE_MERCHANTS = List.of("배달의민족", "쿠팡이츠", "아웃백스테이크하우스", "회식 고깃집", "애슐리퀸즈", "빕스");
    private static final int MAX_ATTEMPTS = 1_000;

    private final TransactionRepository transactionRepository;
    private final Clock clock;
    private final RandomGenerator random;

    /**
     * 이번 달을 뺀 직전 3개월 동안, 매달 모든 카테고리에 2~3건씩 만든다.
     * 메인 서버는 "그 카테고리 지출이 있었던 달 수"로 평균을 내므로 빠지는 달·카테고리가 없어야 한다.
     */
    @Transactional
    public List<Transaction> generateBaseline(UUID userId) {
        YearMonth currentMonth = YearMonth.now(clock);
        YearMonth firstMonth = currentMonth.minusMonths(BASELINE_MONTHS);
        Set<TransactionKey> usedKeys = loadKeys(userId, firstMonth.atDay(1), currentMonth.minusMonths(1).atEndOfMonth());

        List<Transaction> created = new ArrayList<>();
        for (int i = 0; i < BASELINE_MONTHS; i++) {
            YearMonth month = firstMonth.plusMonths(i);
            for (ExpenseCategory category : ExpenseCategory.values()) {
                int count = random.nextInt(2, 4);
                for (int j = 0; j < count; j++) {
                    created.add(newUniqueTransaction(userId, category, category.minAmount(), category.maxAmount(),
                            category.merchants(), month.atDay(1), month.atEndOfMonth(), usedKeys));
                }
            }
        }
        return transactionRepository.saveAll(created);
    }

    /**
     * 이번 달 1일 ~ 오늘 사이 날짜로 카테고리·금액이 랜덤인 거래 10건을 만든다.
     */
    @Transactional
    public List<Transaction> generateRandom(UUID userId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate monthStart = today.withDayOfMonth(1);
        Set<TransactionKey> usedKeys = loadKeys(userId, monthStart, today);
        ExpenseCategory[] categories = ExpenseCategory.values();

        List<Transaction> created = new ArrayList<>();
        for (int i = 0; i < RANDOM_COUNT; i++) {
            ExpenseCategory category = categories[random.nextInt(categories.length)];
            created.add(newUniqueTransaction(userId, category, category.minAmount(), category.maxAmount(),
                    category.merchants(), monthStart, today, usedKeys));
        }
        return transactionRepository.saveAll(created);
    }

    /**
     * 이번 달 식비 합계가 이전 달들의 식비 월평균의 2배 이상이 되도록, 부족한 만큼 이번 달 1일 ~ 오늘 사이 날짜로 만든다.
     * 월평균은 메인 서버와 같이 "이번 달 이전 전체 기간의 식비 합계 / 식비가 있었던 달 수"로 계산한다.
     *
     * @throws IllegalStateException 이전 달 식비 기록이 없을 때 (기준선을 먼저 만들어야 함)
     */
    @Transactional
    public SurgeResult generateFoodSurge(UUID userId) {
        ExpenseCategory food = ExpenseCategory.FOOD;
        LocalDate today = LocalDate.now(clock);
        LocalDate monthStart = today.withDayOfMonth(1);

        List<Transaction> pastFood = transactionRepository.findByFinTargetUserIdAndCategoryAndSpentAtBefore(userId, food.label(), monthStart);
        Map<YearMonth, Long> pastMonthlyTotals = pastFood.stream()
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.getSpentAt()), Collectors.summingLong(Transaction::getAmount)));
        if (pastMonthlyTotals.isEmpty()) {
            throw new IllegalStateException("이전 달 식비 기록이 없습니다. 기준선을 먼저 생성해 주세요.");
        }
        long pastTotal = pastMonthlyTotals.values().stream().mapToLong(Long::longValue).sum();
        // 2 × (합계 / 달 수)를 올림해 정수 비교에서도 2배 이상이 보장되게 한다
        long targetTotal = Math.ceilDiv(SURGE_MULTIPLIER * pastTotal, (long) pastMonthlyTotals.size());

        Set<TransactionKey> usedKeys = loadKeys(userId, monthStart, today);
        long currentTotal = transactionRepository.findByFinTargetUserIdAndSpentAtBetween(userId, monthStart, today).stream()
                .filter(t -> food.label().equals(t.getCategory()))
                .mapToLong(Transaction::getAmount)
                .sum();

        List<Transaction> created = new ArrayList<>();
        while (currentTotal < targetTotal) {
            Transaction transaction = newUniqueTransaction(userId, food, SURGE_MIN_AMOUNT, SURGE_MAX_AMOUNT,
                    SURGE_MERCHANTS, monthStart, today, usedKeys);
            created.add(transaction);
            currentTotal += transaction.getAmount();
        }
        return new SurgeResult(transactionRepository.saveAll(created), Math.ceilDiv(pastTotal, (long) pastMonthlyTotals.size()), currentTotal);
    }

    public record SurgeResult(List<Transaction> created, long pastMonthlyAverage, long currentMonthTotal) {
        public int createdCount() {
            return created.size();
        }
    }

    private Transaction newUniqueTransaction(UUID userId, ExpenseCategory category, long minAmount, long maxAmount,
                                            List<String> merchants, LocalDate from, LocalDate to, Set<TransactionKey> usedKeys) {
        long days = ChronoUnit.DAYS.between(from, to);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            LocalDate spentAt = from.plusDays(random.nextLong(days + 1));
            // 실제 결제처럼 100원 단위 금액
            long amount = random.nextLong(minAmount / 100, maxAmount / 100 + 1) * 100;
            String description = merchants.get(random.nextInt(merchants.size()));
            if (usedKeys.add(new TransactionKey(category.label(), amount, spentAt, description))) {
                return new Transaction(userId, amount, category.label(), description, spentAt);
            }
        }
        throw new IllegalStateException("겹치지 않는 거래 조합을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    private Set<TransactionKey> loadKeys(UUID userId, LocalDate from, LocalDate to) {
        return transactionRepository.findByFinTargetUserIdAndSpentAtBetween(userId, from, to).stream()
                .map(TransactionKey::from)
                .collect(Collectors.toCollection(HashSet::new));
    }

    // 메인 서버 ExpenseService의 중복 판정 키와 같은 조합
    private record TransactionKey(String category, Long amount, LocalDate spentAt, String description) {
        static TransactionKey from(Transaction transaction) {
            return new TransactionKey(transaction.getCategory(), transaction.getAmount(), transaction.getSpentAt(), transaction.getDescription());
        }
    }
}
