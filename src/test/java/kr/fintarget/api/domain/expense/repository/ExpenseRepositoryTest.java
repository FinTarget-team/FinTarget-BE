package kr.fintarget.api.domain.expense.repository;

import kr.fintarget.api.domain.expense.entity.Expense;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
}
