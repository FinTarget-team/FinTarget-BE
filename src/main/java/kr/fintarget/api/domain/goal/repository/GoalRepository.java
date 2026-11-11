package kr.fintarget.api.domain.goal.repository;

import kr.fintarget.api.domain.goal.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {
    Optional<Goal> findByUserIdAndStatus(UUID userId, Goal.GoalStatus status);

    // 사용자의 현재(진행 중) 목표. 다시 정해 보관된 이전 목표는 제외한다 (#202)
    default Optional<Goal> findActiveByUserId(UUID userId) {
        return findByUserIdAndStatus(userId, Goal.GoalStatus.ACTIVE);
    }

    Optional<Goal> findByGoalIdAndUserId(UUID goalId, UUID userId);
    void deleteByUserId(UUID userId);
}