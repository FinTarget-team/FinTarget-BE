package kr.fintarget.api.expense.repository;

import kr.fintarget.api.expense.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
    List<Expense> findByUserId(UUID userId);
    List<Expense> findByUserIdAndSpentAtBetween(UUID userId, LocalDate start, LocalDate end);
    void deleteByUserId(UUID userId);
}