package kr.fintarget.mockbank.transaction.service;

import kr.fintarget.mockbank.transaction.ExpenseCategory;
import kr.fintarget.mockbank.transaction.entity.Transaction;
import kr.fintarget.mockbank.transaction.entity.WebhookStatus;
import kr.fintarget.mockbank.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class DemoDataServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final String FOOD = ExpenseCategory.FOOD.label();

    @Autowired
    private TransactionRepository transactionRepository;

    private final UUID userId = UUID.randomUUID();

    private DemoDataService serviceAt(LocalDate today, long seed) {
        Clock clock = Clock.fixed(today.atStartOfDay(SEOUL).plusHours(12).toInstant(), SEOUL);
        return new DemoDataService(transactionRepository, clock, new Random(seed));
    }

    @Test
    void 기준선은_이번_달을_뺀_직전_3개월_매달_모든_카테고리에_거래를_만든다() {
        int created = serviceAt(TODAY, 1).generateBaseline(userId).size();

        List<Transaction> all = transactionRepository.findAll();
        assertThat(all).hasSize(created);
        int categoryCount = ExpenseCategory.values().length;
        assertThat(created).isBetween(3 * categoryCount * 2, 3 * categoryCount * 3);

        Set<YearMonth> expectedMonths = Set.of(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10));
        Map<YearMonth, Set<String>> categoriesByMonth = all.stream().collect(Collectors.groupingBy(
                t -> YearMonth.from(t.getSpentAt()), Collectors.mapping(Transaction::getCategory, Collectors.toSet())));
        assertThat(categoriesByMonth.keySet()).isEqualTo(expectedMonths);
        categoriesByMonth.values().forEach(categories ->
                assertThat(categories).containsExactlyInAnyOrderElementsOf(ExpenseCategory.labels()));

        assertThat(all).allSatisfy(t -> {
            assertThat(t.getFinTargetUserId()).isEqualTo(userId);
            assertThat(t.getAmount()).isPositive();
            assertThat(t.getAmount() % 100).isZero();
            assertThat(t.getWebhookStatus()).isEqualTo(WebhookStatus.PENDING);
        });
    }

    @Test
    void 랜덤_거래는_이번_달_1일부터_오늘_사이_날짜로_10건을_만든다() {
        int created = serviceAt(TODAY, 2).generateRandom(userId).size();

        assertThat(created).isEqualTo(DemoDataService.RANDOM_COUNT);
        assertThat(transactionRepository.findAll()).allSatisfy(t -> {
            assertThat(t.getSpentAt()).isBetween(LocalDate.of(2026, 11, 1), TODAY);
            assertThat(ExpenseCategory.labels()).contains(t.getCategory());
        });
    }

    @Test
    void 식비_급증은_기준선이_없으면_아무것도_만들지_않고_거부한다() {
        assertThatThrownBy(() -> serviceAt(TODAY, 3).generateFoodSurge(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("기준선을 먼저 생성해 주세요");
        assertThat(transactionRepository.findAll()).isEmpty();
    }

    @Test
    void 식비_급증_후_이번_달_식비는_메인_서버_방식의_이전_월평균의_2배_이상이다() {
        DemoDataService service = serviceAt(TODAY, 4);
        service.generateBaseline(userId);
        service.generateRandom(userId);

        DemoDataService.SurgeResult result = service.generateFoodSurge(userId);

        // 메인 서버 NudgeService와 같은 계산: 이번 달 이전 식비 합계 / 식비가 있었던 달 수
        List<Transaction> all = transactionRepository.findAll();
        YearMonth currentMonth = YearMonth.from(TODAY);
        List<Transaction> pastFood = all.stream()
                .filter(t -> t.getCategory().equals(FOOD) && YearMonth.from(t.getSpentAt()).isBefore(currentMonth))
                .toList();
        long pastMonths = pastFood.stream().map(t -> YearMonth.from(t.getSpentAt())).distinct().count();
        double average = (double) pastFood.stream().mapToLong(Transaction::getAmount).sum() / pastMonths;
        long currentFood = all.stream()
                .filter(t -> t.getCategory().equals(FOOD) && YearMonth.from(t.getSpentAt()).equals(currentMonth))
                .mapToLong(Transaction::getAmount).sum();

        assertThat(result.createdCount()).isPositive();
        assertThat(currentFood).isEqualTo(result.currentMonthTotal());
        assertThat((double) currentFood).isGreaterThanOrEqualTo(average * 2);
        assertThat(all).filteredOn(t -> YearMonth.from(t.getSpentAt()).equals(currentMonth))
                .allSatisfy(t -> assertThat(t.getSpentAt()).isBetween(LocalDate.of(2026, 11, 1), TODAY));
    }

    @Test
    void 식비_급증을_다시_누르면_이미_2배_이상이라_새로_만들지_않는다() {
        DemoDataService service = serviceAt(TODAY, 5);
        service.generateBaseline(userId);
        service.generateFoodSurge(userId);
        long before = transactionRepository.count();

        DemoDataService.SurgeResult second = service.generateFoodSurge(userId);

        assertThat(second.createdCount()).isZero();
        assertThat(transactionRepository.count()).isEqualTo(before);
    }

    @Test
    void 이번_달_1일에도_오늘_날짜로_식비_급증을_만든다() {
        LocalDate firstDay = LocalDate.of(2026, 11, 1);
        DemoDataService service = serviceAt(firstDay, 6);
        service.generateBaseline(userId);

        DemoDataService.SurgeResult result = service.generateFoodSurge(userId);

        assertThat(result.createdCount()).isPositive();
        assertThat(transactionRepository.findAll())
                .filteredOn(t -> YearMonth.from(t.getSpentAt()).equals(YearMonth.of(2026, 11)))
                .allSatisfy(t -> assertThat(t.getSpentAt()).isEqualTo(firstDay));
    }

    @Test
    void 시연_버튼을_여러_번_눌러도_같은_사용자의_날짜_카테고리_금액_설명_조합이_겹치지_않는다() {
        DemoDataService service = serviceAt(TODAY, 7);
        for (int i = 0; i < 3; i++) {
            service.generateBaseline(userId);
            service.generateRandom(userId);
        }
        service.generateFoodSurge(userId);

        List<Transaction> all = transactionRepository.findAll();
        Set<List<Object>> keys = new HashSet<>();
        all.forEach(t -> keys.add(List.of(t.getCategory(), t.getAmount(), t.getSpentAt(), t.getDescription())));
        assertThat(keys).hasSize(all.size());
    }

    @Test
    void 다른_사용자의_거래는_식비_평균_계산에_섞이지_않는다() {
        UUID otherUser = UUID.randomUUID();
        DemoDataService service = serviceAt(TODAY, 8);
        service.generateBaseline(otherUser);

        assertThatThrownBy(() -> service.generateFoodSurge(userId))
                .isInstanceOf(IllegalStateException.class);
    }
}
