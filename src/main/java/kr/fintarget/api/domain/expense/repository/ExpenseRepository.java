package kr.fintarget.api.domain.expense.repository;

import kr.fintarget.api.domain.expense.dto.ExpenseCategoryStat;
import kr.fintarget.api.domain.expense.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
    List<Expense> findByUserId(UUID userId);
    List<Expense> findByUserIdAndSpentAtBetween(UUID userId, LocalDate start, LocalDate end);
    void deleteByUserId(UUID userId);

    @Query("SELECT new kr.fintarget.api.domain.expense.dto.ExpenseCategoryStat(e.category, SUM(e.amount)) " +
            "FROM Expense e WHERE e.userId = :userId AND e.spentAt BETWEEN :start AND :end " +
            "GROUP BY e.category")
    List<ExpenseCategoryStat> sumByCategory(@Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    // 컬럼을 지정하지 않은 ON CONFLICT DO NOTHING: expense는 PK 외에 unique 제약이
    // 하나뿐이라 이 형태로도 의미가 명확하고, @DataJpaTest에서 쓰는 H2의 제한적인
    // ON CONFLICT 문법 지원과도 호환된다.
    @Modifying
    @Query(value = "INSERT INTO expense (expense_id, user_id, category, amount, description, spent_at, created_at) " +
            "VALUES (:expenseId, :userId, :category, :amount, :description, :spentAt, :createdAt) " +
            "ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int insertIfNotExists(@Param("expenseId") UUID expenseId, @Param("userId") UUID userId,
                           @Param("category") String category, @Param("amount") Long amount,
                           @Param("description") String description, @Param("spentAt") LocalDate spentAt,
                           @Param("createdAt") LocalDateTime createdAt);
}