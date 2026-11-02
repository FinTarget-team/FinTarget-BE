package kr.fintarget.mockbank.transaction.service;

import kr.fintarget.mockbank.transaction.entity.Transaction;
import kr.fintarget.mockbank.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 메인 서버 NudgeService.generateUnusualSpendingNudge와 같은 판정을 하는지 확인한다.
 */
@DataJpaTest
class NudgeForecastServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

    @Autowired
    private TransactionRepository transactionRepository;

    private NudgeForecastService forecastService;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        forecastService = new NudgeForecastService(transactionRepository,
                Clock.fixed(TODAY.atStartOfDay(SEOUL).plusHours(12).toInstant(), SEOUL));
    }

    private void save(String category, long amount, LocalDate spentAt) {
        transactionRepository.save(new Transaction(userId, amount, category, category + amount, spentAt));
    }

    @Test
    void 이전_월평균은_지출이_있었던_달_수로_나누고_1_5배_이상이면_넛지_대상이다() {
        // 8월 30,000 + 10월 30,000 (9월 없음) → 평균 30,000 (3개월이 아니라 2개월로 나눔)
        save("식비", 30_000, LocalDate.of(2026, 8, 10));
        save("식비", 30_000, LocalDate.of(2026, 10, 10));
        save("식비", 45_000, LocalDate.of(2026, 11, 3));

        assertThat(forecastService.forecast(userId)).containsExactly(
                new NudgeForecastService.CategoryForecast("식비", 45_000, 30_000L, 1.5, true));
    }

    @Test
    void 이전_월평균의_1_5배보다_조금이라도_적으면_넛지_대상이_아니다() {
        save("교통", 20_000, LocalDate.of(2026, 10, 10));
        save("교통", 29_900, LocalDate.of(2026, 11, 3));

        NudgeForecastService.CategoryForecast forecast = forecastService.forecast(userId).getFirst();

        assertThat(forecast.nudge()).isFalse();
        assertThat(forecast.ratio()).isEqualTo(1.495);
    }

    @Test
    void 이전_달_기록이_없는_카테고리는_넛지를_만들지_않는다() {
        save("쇼핑", 500_000, LocalDate.of(2026, 11, 3));

        assertThat(forecastService.forecast(userId)).containsExactly(
                new NudgeForecastService.CategoryForecast("쇼핑", 500_000, null, null, false));
    }

    @Test
    void 이번_달_지출이_없는_카테고리는_결과에_넣지_않는다() {
        save("생활", 50_000, LocalDate.of(2026, 10, 10));
        save("식비", 10_000, LocalDate.of(2026, 10, 10));
        save("식비", 10_000, LocalDate.of(2026, 11, 1));

        assertThat(forecastService.forecast(userId)).extracting(NudgeForecastService.CategoryForecast::category)
                .containsExactly("식비");
    }

    @Test
    void 결과는_카테고리_순서대로_정렬되고_다른_사용자_거래는_섞이지_않는다() {
        save("기타", 1_000, LocalDate.of(2026, 11, 2));
        save("식비", 1_000, LocalDate.of(2026, 11, 2));
        save("교통", 1_000, LocalDate.of(2026, 11, 2));
        transactionRepository.save(new Transaction(UUID.randomUUID(), 9_999L, "쇼핑", "다른 사용자", LocalDate.of(2026, 11, 2)));

        List<String> categories = forecastService.forecast(userId).stream()
                .map(NudgeForecastService.CategoryForecast::category)
                .toList();

        assertThat(categories).containsExactly("식비", "교통", "기타");
    }
}
