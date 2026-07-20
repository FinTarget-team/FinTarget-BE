package kr.fintarget.api.domain.expense.repository;

import kr.fintarget.api.domain.expense.entity.Expense;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
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
}
