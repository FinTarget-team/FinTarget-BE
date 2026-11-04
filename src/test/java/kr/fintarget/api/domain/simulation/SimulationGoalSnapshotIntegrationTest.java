package kr.fintarget.api.domain.simulation;

import kr.fintarget.api.domain.goal.dto.GoalUpdateRequest;
import kr.fintarget.api.domain.goal.entity.Goal;
import kr.fintarget.api.domain.goal.repository.GoalRepository;
import kr.fintarget.api.domain.goal.service.GoalService;
import kr.fintarget.api.domain.simulation.dto.SimulationRequest;
import kr.fintarget.api.domain.simulation.dto.SimulationResponse;
import kr.fintarget.api.domain.simulation.repository.SimulationRepository;
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

/**
 * 실제 DB(H2)에 실행 당시 목표 값이 저장·조회되는지, 이 컬럼이 생기기 전의 기록(값 없음)도 오류 없이 조회되는지 확인한다.
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
    private SimulationRepository simulationRepository;

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
        simulationRepository.deleteAll(simulationRepository.findByUserId(userId));
        goalRepository.deleteById(goal.getGoalId());
    }

    private SimulationResponse run() {
        return simulationService.runSimulation(userId, new SimulationRequest(goal.getGoalId(), 1_000_000L, null, null));
    }

    @Test
    void 목표를_수정한_뒤에도_이전_기록은_실행_당시_값으로_조회된다() {
        SimulationResponse first = run();
        goalService.updateGoal(userId, new GoalUpdateRequest("내 집 마련", 80_000_000L, 5_000_000L, LocalDate.now().plusYears(3)));
        SimulationResponse second = run();

        List<SimulationResponse> history = simulationService.getSimulationsByGoal(userId, goal.getGoalId());

        assertThat(history).hasSize(2);
        SimulationResponse storedFirst = history.stream().filter(r -> r.simulationId().equals(first.simulationId())).findFirst().orElseThrow();
        SimulationResponse storedSecond = history.stream().filter(r -> r.simulationId().equals(second.simulationId())).findFirst().orElseThrow();
        assertThat(storedFirst.goalId()).isEqualTo(storedSecond.goalId());
        assertThat(storedFirst.goalTitle()).isEqualTo("전세 자금");
        assertThat(storedFirst.goalTargetAmount()).isEqualTo(30_000_000L);
        assertThat(storedSecond.goalTitle()).isEqualTo("내 집 마련");
        assertThat(storedSecond.goalTargetAmount()).isEqualTo(80_000_000L);
        assertThat(storedSecond.goalCurrentAmount()).isEqualTo(5_000_000L);
    }

    @Test
    void 실행_당시_값이_없는_기존_기록도_null로_조회된다() {
        SimulationResponse legacy = run();
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
