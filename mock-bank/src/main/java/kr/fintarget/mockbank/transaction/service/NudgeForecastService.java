package kr.fintarget.mockbank.transaction.service;

import kr.fintarget.mockbank.transaction.ExpenseCategory;
import kr.fintarget.mockbank.transaction.entity.Transaction;
import kr.fintarget.mockbank.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 메인 서버 NudgeService의 이상 지출(UNUSUAL_SPENDING) 판정을 mock-bank 거래로 미리 계산한다.
 * - 이전 월평균 = 이번 달 이전 전체 기간의 카테고리 합계 / 그 카테고리 지출이 있었던 달 수
 * - 이번 달 합계가 이전 월평균의 1.5배 이상이면 넛지 대상
 * - 이번 달 지출이 없거나 이전 달 기록이 없는 카테고리는 넛지를 만들지 않는다
 * 메인 서버에 이 사용자의 다른 지출(앱에서 직접 추가 등)이 있으면 결과가 다를 수 있다.
 */
@Service
@RequiredArgsConstructor
public class NudgeForecastService {

    // 메인 서버 NudgeService.UNUSUAL_SPENDING_RATIO와 같은 값
    public static final double NUDGE_RATIO = 1.5;

    private final TransactionRepository transactionRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<CategoryForecast> forecast(UUID userId) {
        return categoryStats(userId).values().stream()
                .filter(stats -> stats.currentTotal() > 0)
                .map(CategoryStats::toForecast)
                .sorted(Comparator.comparingInt(forecast -> categoryOrder(forecast.category())))
                .toList();
    }

    /**
     * 카테고리별 이번 달 합계와 이전 달 합계·달 수. 기록이 있는 카테고리만 담는다.
     */
    @Transactional(readOnly = true)
    public Map<String, CategoryStats> categoryStats(UUID userId) {
        YearMonth currentMonth = YearMonth.now(clock);
        Map<String, Long> currentTotals = new HashMap<>();
        Map<String, Long> pastTotals = new HashMap<>();
        Map<String, Set<YearMonth>> pastMonths = new HashMap<>();

        for (Transaction transaction : transactionRepository.findByFinTargetUserId(userId)) {
            YearMonth month = YearMonth.from(transaction.getSpentAt());
            if (month.equals(currentMonth)) {
                currentTotals.merge(transaction.getCategory(), transaction.getAmount(), Long::sum);
            } else if (month.isBefore(currentMonth)) {
                pastTotals.merge(transaction.getCategory(), transaction.getAmount(), Long::sum);
                pastMonths.computeIfAbsent(transaction.getCategory(), key -> new HashSet<>()).add(month);
            }
        }

        Set<String> categories = new HashSet<>(currentTotals.keySet());
        categories.addAll(pastTotals.keySet());
        Map<String, CategoryStats> stats = new HashMap<>();
        for (String category : categories) {
            stats.put(category, new CategoryStats(category,
                    currentTotals.getOrDefault(category, 0L),
                    pastTotals.getOrDefault(category, 0L),
                    pastMonths.getOrDefault(category, Set.of()).size()));
        }
        return stats;
    }

    public record CategoryStats(String category, long currentTotal, long pastTotal, int pastMonthCount) {

        public boolean hasPastAverage() {
            return pastMonthCount > 0 && pastTotal > 0;
        }

        public double pastAverage() {
            return hasPastAverage() ? (double) pastTotal / pastMonthCount : 0;
        }

        public CategoryStats plusCurrent(long amount) {
            return new CategoryStats(category, currentTotal + amount, pastTotal, pastMonthCount);
        }

        CategoryForecast toForecast() {
            if (!hasPastAverage()) {
                return new CategoryForecast(category, currentTotal, null, null, false);
            }
            double average = pastAverage();
            return new CategoryForecast(category, currentTotal, Math.round(average), currentTotal / average,
                    currentTotal >= average * NUDGE_RATIO);
        }
    }

    /**
     * @param pastAverage 이전 달 기록이 없으면 null
     * @param ratio       이번 달 합계 / 이전 월평균. 이전 달 기록이 없으면 null
     * @param nudge       메인 서버가 이상 지출 넛지를 만드는지
     */
    public record CategoryForecast(String category, long currentTotal, Long pastAverage, Double ratio, boolean nudge) {
    }

    private static int categoryOrder(String label) {
        return ExpenseCategory.fromLabel(label).map(Enum::ordinal).orElse(Integer.MAX_VALUE);
    }
}
