package kr.fintarget.api.domain.goal.repository;

import kr.fintarget.api.domain.goal.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {
    Optional<Goal> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}