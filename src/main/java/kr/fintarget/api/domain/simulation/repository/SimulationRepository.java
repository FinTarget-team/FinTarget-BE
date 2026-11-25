package kr.fintarget.api.domain.simulation.repository;

import kr.fintarget.api.domain.policy.entity.UserPolicy;
import kr.fintarget.api.domain.simulation.entity.Simulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface SimulationRepository extends JpaRepository<Simulation, UUID> {
    List<Simulation> findByUserId(UUID userId);
    List<Simulation> findByUserIdAndGoalGoalId(UUID userId, UUID goalId);
    void deleteAllByGoalGoalId(UUID goalId);
    /** 지운 건수를 돌려준다 (시연 초기화 응답, #239) */
    long deleteByUserId(UUID userId);

    /**
     * 스크랩을 삭제하기 전에, 그 스크랩으로 실행한 시뮬레이션 기록에서 연결만 끊는다 (#248).
     * 시뮬레이션은 실행 당시 계산 결과(정책 반영 달성일·확률)를 그대로 갖고 있으므로 기록은 남긴다.
     * 끊지 않으면 외래 키 때문에 스크랩을 지울 수 없다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Simulation s set s.userPolicy = null where s.userPolicy = :userPolicy")
    int detachUserPolicy(@Param("userPolicy") UserPolicy userPolicy);
}