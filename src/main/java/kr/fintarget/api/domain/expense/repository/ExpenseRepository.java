package kr.fintarget.api.domain.expense.repository;

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

    // Untargeted ON CONFLICT DO NOTHING: expense has only the one unique constraint
    // (uk_expense_..._description) besides its PK, so this is unambiguous and also
    // works against H2's more limited ON CONFLICT support in @DataJpaTest.
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