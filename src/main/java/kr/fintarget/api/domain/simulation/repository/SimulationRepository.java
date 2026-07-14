package kr.fintarget.api.domain.simulation.repository;

import kr.fintarget.api.domain.simulation.entity.Simulation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SimulationRepository extends JpaRepository<Simulation, UUID> {
    List<Simulation> findByUserId(UUID userId);
    List<Simulation> findByUserIdAndGoalGoalId(UUID userId, UUID goalId);
    void deleteAllByGoalGoalId(UUID goalId);
    void deleteByUserId(UUID userId);
}