package kr.fintarget.api.domain.goal.service;

import kr.fintarget.api.domain.goal.dto.GoalCreateRequest;
import kr.fintarget.api.domain.goal.dto.GoalUpdateRequest;
import kr.fintarget.api.domain.goal.dto.GoalResponse;
import kr.fintarget.api.domain.goal.entity.Goal;
import kr.fintarget.api.domain.goal.repository.GoalRepository;
import kr.fintarget.api.domain.simulation.repository.SimulationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;
    private final SimulationRepository simulationRepository;

    @Transactional
    public GoalResponse createGoal(UUID userId, GoalCreateRequest request) {
        if (goalRepository.findByUserId(userId).isPresent()) {
            throw new IllegalStateException("이미 목표가 존재합니다.");
        }

        Goal goal = new Goal(
                userId,
                request.title(),
                request.targetAmount(),
                request.currentAmount(),
                request.deadline()
        );

        return GoalResponse.from(goalRepository.save(goal));
    }

    @Transactional(readOnly = true)
    public GoalResponse getGoal(UUID userId) {
        return goalRepository.findByUserId(userId)
                .map(GoalResponse::from)
                .orElse(null);
    }

    @Transactional
    public GoalResponse updateGoal(UUID userId, GoalUpdateRequest request) {
        Goal goal = goalRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("목표가 존재하지 않습니다."));

        goal.update(
                request.title(),
                request.targetAmount(),
                request.currentAmount(),
                request.deadline()
        );

        return GoalResponse.from(goal);
    }

    @Transactional
    public void deleteGoal(UUID userId) {
        Goal goal = goalRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("목표가 존재하지 않습니다."));

        simulationRepository.deleteAllByGoalGoalId(goal.getGoalId());
        goalRepository.delete(goal);
    }
}