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
        if (goalRepository.findActiveByUserId(userId).isPresent()) {
            throw new IllegalStateException("이미 목표가 존재합니다.");
        }
        Goal.validateTargetAmount(request.targetAmount());

        Goal goal = new Goal(
                userId,
                request.title(),
                request.targetAmount(),
                request.currentAmount(),
                request.deadline()
        );

        return GoalResponse.from(goalRepository.save(goal));
    }

    // 온보딩 마지막 단계(목표 금액, 현재 7단계) 재제출(재시도)에도 안전하도록, 목표가 있으면 금액만 갱신한다
    @Transactional
    public void upsertFromOnboarding(UUID userId, long targetAmount) {
        Goal.validateTargetAmount(targetAmount);
        goalRepository.findActiveByUserId(userId).ifPresentOrElse(
                goal -> goal.updateTargetAmount(targetAmount),
                () -> goalRepository.save(Goal.createFromOnboarding(userId, targetAmount))
        );
    }

    @Transactional(readOnly = true)
    public GoalResponse getGoal(UUID userId) {
        return goalRepository.findActiveByUserId(userId)
                .map(GoalResponse::from)
                .orElse(null);
    }

    @Transactional
    public GoalResponse updateGoal(UUID userId, GoalUpdateRequest request) {
        Goal goal = goalRepository.findActiveByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("목표가 존재하지 않습니다."));
        Goal.validateTargetAmount(request.targetAmount());

        if (!goal.isRedefinedBy(request.targetAmount(), request.deadline())) {
            goal.update(
                    request.title(),
                    request.targetAmount(),
                    request.currentAmount(),
                    request.deadline()
            );
            return GoalResponse.from(goal);
        }

        // 목표를 다시 정했다면 이전 목표와 그 시뮬레이션 기록은 남기고 새 goalId로 목표를 만든다 (#202).
        // Hibernate는 flush할 때 INSERT를 UPDATE보다 먼저 실행하므로, 보관 처리를 먼저 반영하지 않으면
        // 진행 중 목표 부분 유니크 인덱스(사용자당 ACTIVE 1개)에 걸린다
        goal.archive();
        goalRepository.flush();

        Goal redefined = new Goal(
                userId,
                request.title(),
                request.targetAmount(),
                request.currentAmount(),
                request.deadline()
        );
        return GoalResponse.from(goalRepository.save(redefined));
    }

    // 진행 중인 목표와 그 시뮬레이션만 지운다. 다시 정해 보관된 이전 목표와 기록은 남긴다 (#202)
    @Transactional
    public void deleteGoal(UUID userId) {
        Goal goal = goalRepository.findActiveByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("목표가 존재하지 않습니다."));

        simulationRepository.deleteAllByGoalGoalId(goal.getGoalId());
        goalRepository.delete(goal);
    }
}