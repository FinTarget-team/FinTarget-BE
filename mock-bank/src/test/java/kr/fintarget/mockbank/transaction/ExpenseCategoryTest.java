package kr.fintarget.mockbank.transaction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseCategoryTest {

    @Test
    void 카테고리는_iOS_앱이_보내는_6개_값과_같다() {
        // FinTarget-iOS AddExpenseView.categories와 순서까지 같게 유지한다 (#165)
        assertThat(ExpenseCategory.labels()).containsExactly("식비", "교통", "쇼핑", "문화/여가", "생활", "기타");
    }

    @Test
    void 모든_카테고리는_금액_범위와_가맹점이_있다() {
        for (ExpenseCategory category : ExpenseCategory.values()) {
            assertThat(category.minAmount()).isPositive();
            assertThat(category.maxAmount()).isGreaterThan(category.minAmount());
            assertThat(category.merchants()).isNotEmpty();
        }
    }
}
