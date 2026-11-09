package kr.fintarget.api.domain.simulation;

import kr.fintarget.api.domain.goal.dto.GoalResponse;
import kr.fintarget.api.domain.goal.dto.GoalUpdateRequest;
import kr.fintarget.api.domain.goal.entity.Goal;
import kr.fintarget.api.domain.goal.repository.GoalRepository;
import kr.fintarget.api.domain.goal.service.GoalService;
import kr.fintarget.api.domain.simulation.dto.SimulationRequest;
import kr.fintarget.api.domain.simulation.dto.SimulationResponse;
import kr.fintarget.api.domain.simulation.service.SimulationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 DB(H2)에서 목표 수정과 시뮬레이션 기록이 어떻게 이어지는지 확인한다.
 * - 실행 당시 목표 값이 기록에 남는지 (#186)
 * - 목표 금액·마감일을 다시 정하면 새 goalId가 생기고, 이전 목표와 기록이 보존되는지 (#202)
 */
@SpringBootTest
class SimulationGoalSnapshotIntegrationTest {

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private GoalService goalService;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID userId = UUID.randomUUID();
    private Goal goal;

    @BeforeEach
    void setUp() {
        goal = goalRepository.save(new Goal(userId, "전세 자금", 30_000_000L, 0L, LocalDate.now().plusYears(2)));
    }

    @AfterEach
    void tearDown() {
        // 목표를 다시 정하면 사용자의 목표가 여러 줄이 되므로 사용자 기준으로 지운다
        jdbcTemplate.update("delete from simulation where user_id = ?", userId);
        jdbcTemplate.update("delete from goal where user_id = ?", userId);
    }

    private SimulationResponse run(UUID goalId) {
        return simulationService.runSimulation(userId, new SimulationRequest(goalId, 1_000_000L, null, null));
    }

    @Test
    void 목표_금액을_다시_정하면_새_goalId가_생기고_기록이_목표별로_나뉜다() {
        SimulationResponse first = run(goal.getGoalId());

        GoalResponse redefined = goalService.updateGoal(userId,
                new GoalUpdateRequest("내 집 마련", 80_000_000L, 5_000_000L, goal.getDeadline()));
        SimulationResponse second = run(redefined.goalId());

        assertThat(redefined.goalId()).isNotEqualTo(goal.getGoalId());
        assertThat(goalService.getGoal(userId).goalId()).isEqualTo(redefined.goalId());
        assertThat(goalRepository.findById(goal.getGoalId())).get().satisfies(previous -> {
            assertThat(previous.getStatus()).isEqualTo(Goal.GoalStatus.ARCHIVED);
            assertThat(previous.getTitle()).isEqualTo("전세 자금");
            assertThat(previous.getTargetAmount()).isEqualTo(30_000_000L);
        });

        assertThat(simulationService.getSimulationsByGoal(userId, redefined.goalId()))
                .extracting(SimulationResponse::simulationId)
                .containsExactly(second.simulationId());
        assertThat(simulationService.getSimulationsByGoal(userId, goal.getGoalId()))
                .singleElement().satisfies(previous -> {
                    assertThat(previous.simulationId()).isEqualTo(first.simulationId());
                    assertThat(previous.goalTitle()).isEqualTo("전세 자금");
                    assertThat(previous.goalTargetAmount()).isEqualTo(30_000_000L);
                });
        assertThat(simulationService.getSimulations(userId))
                .extracting(SimulationResponse::simulationId)
                .containsExactlyInAnyOrder(first.simulationId(), second.simulationId());
    }

    @Test
    void 마감일을_다시_정해도_새_goalId가_생긴다() {
        GoalResponse redefined = goalService.updateGoal(userId,
                new GoalUpdateRequest("전세 자금", 30_000_000L, 0L, goal.getDeadline().plusYears(1)));

        assertThat(redefined.goalId()).isNotEqualTo(goal.getGoalId());
        assertThat(redefined.deadline()).isEqualTo(goal.getDeadline().plusYears(1));
    }

    @Test
    void 제목과_모은_금액만_바꾸면_goalId와_기록이_이어지고_실행_당시_값으로_구분된다() {
        SimulationResponse first = run(goal.getGoalId());

        GoalResponse updated = goalService.updateGoal(userId,
                new GoalUpdateRequest("내 전세 자금", 30_000_000L, 5_000_000L, goal.getDeadline()));
        SimulationResponse second = run(updated.goalId());

        assertThat(updated.goalId()).isEqualTo(goal.getGoalId());
        List<SimulationResponse> history = simulationService.getSimulationsByGoal(userId, goal.getGoalId());
        assertThat(history).hasSize(2);
        SimulationResponse storedFirst = history.stream().filter(r -> r.simulationId().equals(first.simulationId())).findFirst().orElseThrow();
        SimulationResponse storedSecond = history.stream().filter(r -> r.simulationId().equals(second.simulationId())).findFirst().orElseThrow();
        assertThat(storedFirst.goalTitle()).isEqualTo("전세 자금");
        assertThat(storedFirst.goalCurrentAmount()).isZero();
        assertThat(storedSecond.goalTitle()).isEqualTo("내 전세 자금");
        assertThat(storedSecond.goalCurrentAmount()).isEqualTo(5_000_000L);
    }

    @Test
    void 보관된_이전_목표로는_시뮬레이션을_실행할_수_없다() {
        goalService.updateGoal(userId,
                new GoalUpdateRequest("내 집 마련", 80_000_000L, 0L, goal.getDeadline()));

        assertThatThrownBy(() -> run(goal.getGoalId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("현재 진행 중인 목표가 아닙니다. 최신 목표로 다시 시도해주세요.");
    }

    @Test
    void 목표를_삭제하면_진행_중인_목표만_지우고_보관된_목표와_기록은_남는다() {
        SimulationResponse first = run(goal.getGoalId());
        GoalResponse redefined = goalService.updateGoal(userId,
                new GoalUpdateRequest("내 집 마련", 80_000_000L, 0L, goal.getDeadline()));
        run(redefined.goalId());

        goalService.deleteGoal(userId);

        assertThat(goalService.getGoal(userId)).isNull();
        assertThat(goalRepository.findById(redefined.goalId())).isEmpty();
        assertThat(goalRepository.findById(goal.getGoalId())).isPresent();
        assertThat(simulationService.getSimulations(userId))
                .extracting(SimulationResponse::simulationId)
                .containsExactly(first.simulationId());
    }

    @Test
    void 실행_당시_값이_없는_기존_기록도_null로_조회된다() {
        SimulationResponse legacy = run(goal.getGoalId());
        // 컬럼이 추가되기 전에 저장된 운영 기록과 같은 상태로 만든다
        jdbcTemplate.update("update simulation set goal_title = null, goal_target_amount = null, "
                + "goal_current_amount = null, goal_deadline = null where simulation_id = ?", legacy.simulationId());

        List<SimulationResponse> history = simulationService.getSimulations(userId);

        assertThat(history).singleElement().satisfies(response -> {
            assertThat(response.goalId()).isEqualTo(goal.getGoalId());
            assertThat(response.goalTitle()).isNull();
            assertThat(response.goalTargetAmount()).isNull();
            assertThat(response.goalCurrentAmount()).isNull();
            assertThat(response.goalDeadline()).isNull();
            assertThat(response.expectedCompletionDate()).isNotNull();
        });
    }
}
