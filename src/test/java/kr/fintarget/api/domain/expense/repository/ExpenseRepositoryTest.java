package kr.fintarget.api.domain.expense.repository;

import kr.fintarget.api.domain.expense.dto.ExpenseCategoryStat;
import kr.fintarget.api.domain.expense.entity.Expense;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:expense-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class ExpenseRepositoryTest {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Test
    void 동일한_유저_카테고리_금액_날짜_설명의_지출은_중복_저장되지_않는다() {
        UUID userId = UUID.randomUUID();
        LocalDate spentAt = LocalDate.of(2026, 7, 1);
        expenseRepository.saveAndFlush(new Expense(userId, 10000L, "FOOD", "점심", spentAt));

        assertThatThrownBy(() ->
                expenseRepository.saveAndFlush(new Expense(userId, 10000L, "FOOD", "점심", spentAt))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void insertIfNotExists는_충돌시_예외없이_스킵한다() {
        UUID userId = UUID.randomUUID();
        LocalDate spentAt = LocalDate.of(2026, 7, 1);

        int firstResult = expenseRepository.insertIfNotExists(
                UUID.randomUUID(), userId, "FOOD", 10000L, "점심", spentAt, LocalDateTime.now());
        int secondResult = expenseRepository.insertIfNotExists(
                UUID.randomUUID(), userId, "FOOD", 10000L, "점심", spentAt, LocalDateTime.now());

        assertThat(firstResult).isEqualTo(1);
        assertThat(secondResult).isEqualTo(0);
        assertThat(expenseRepository.findByUserId(userId)).hasSize(1);
    }

    @Test
    void sumByCategory는_기간_내_카테고리별_합계를_반환한다() {
        UUID userId = UUID.randomUUID();
        LocalDate spentAt = LocalDate.of(2026, 7, 1);
        expenseRepository.save(new Expense(userId, 10000L, "FOOD", "점심", spentAt));
        expenseRepository.save(new Expense(userId, 5000L, "FOOD", "커피", spentAt));
        expenseRepository.save(new Expense(userId, 30000L, "TRANSPORT", "택시", spentAt));
        // 기간 밖 데이터는 집계에서 제외되어야 함
        expenseRepository.save(new Expense(userId, 999999L, "FOOD", "기간밖", spentAt.minusMonths(2)));

        List<ExpenseCategoryStat> result = expenseRepository.sumByCategory(userId, spentAt.minusDays(1), spentAt.plusDays(1));

        assertThat(result).containsExactlyInAnyOrder(
                new ExpenseCategoryStat("FOOD", 15000L),
                new ExpenseCategoryStat("TRANSPORT", 30000L)
        );
    }

    @Test
    void sumByCategory는_지출이_없으면_빈_리스트를_반환한다() {
        UUID userId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);

        assertThat(expenseRepository.sumByCategory(userId, start, end)).isEmpty();
    }
}
