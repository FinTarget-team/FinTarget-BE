package kr.fintarget.api.simulation.repository;

import kr.fintarget.api.simulation.entity.Simulation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SimulationRepository extends JpaRepository<Simulation, UUID> {
    List<Simulation> findByUserId(UUID userId);
    List<Simulation> findByUserIdAndGoalGoalId(UUID userId, UUID goalId);
    void deleteAllByGoalGoalId(UUID goalId);
    void deleteByUserId(UUID userId);
}