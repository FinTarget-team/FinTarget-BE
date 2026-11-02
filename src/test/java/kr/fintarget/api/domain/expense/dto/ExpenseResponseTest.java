package kr.fintarget.api.domain.expense.dto;

import kr.fintarget.api.domain.expense.entity.Expense;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseResponseTest {

    @Test
    void 설명이_null이면_빈_문자열로_내려준다() {
        // iOS ExpenseDTO.description은 non-optional String이라 null이면 목록 전체 디코딩이 실패한다
        Expense expense = new Expense(UUID.randomUUID(), 1000L, "식비", null, LocalDate.of(2026, 11, 1));

        assertThat(ExpenseResponse.from(expense).description()).isEmpty();
    }

    @Test
    void 설명이_있으면_그대로_내려준다() {
        Expense expense = new Expense(UUID.randomUUID(), 1000L, "식비", "김밥천국", LocalDate.of(2026, 11, 1));

        assertThat(ExpenseResponse.from(expense).description()).isEqualTo("김밥천국");
    }
}
