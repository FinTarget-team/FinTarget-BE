package kr.fintarget.api.domain.policy.service;

import jakarta.persistence.EntityManager;
import kr.fintarget.api.domain.goal.entity.Goal;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.entity.UserPolicy;
import kr.fintarget.api.domain.simulation.entity.Simulation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시뮬레이션을 실행한 스크랩도 삭제할 수 있는지 실제 DB 외래 키로 확인한다 (#248).
 * 이전에는 simulation.user_policy_id 외래 키 때문에 삭제가 거부되어 500이 났다.
 */
@SpringBootTest
@Transactional
class UserPolicyDeleteIntegrationTest {

    @Autowired
    private PolicyService policyService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 시뮬레이션을_실행한_스크랩도_삭제되고_시뮬레이션_기록은_남는다() {
        UUID userId = UUID.randomUUID();
        Policy policy = Policy.create("청년 월세 지원", "설명", 19, 34, null, 200_000L,
                PolicyType.HOUSING, "SEOUL", "scrap-delete-test", "TEST");
        entityManager.persist(policy);
        UserPolicy userPolicy = new UserPolicy(userId, policy, UserPolicy.UserPolicyStatus.INTEREST);
        entityManager.persist(userPolicy);
        Goal goal = new Goal(userId, "보증금 모으기", 10_000_000L, 1_000_000L, LocalDate.now().plusYears(1));
        entityManager.persist(goal);
        LocalDate expected = LocalDate.now().plusMonths(10);
        LocalDate withPolicy = LocalDate.now().plusMonths(8);
        Simulation simulation = new Simulation(userId, goal, userPolicy, 900_000L, expected,
                withPolicy, expected.minusMonths(1), expected.plusMonths(1), 0.6, 0.7);
        entityManager.persist(simulation);
        entityManager.flush();
        entityManager.clear();

        policyService.deleteUserPolicy(userId, userPolicy.getUserPolicyId());
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(UserPolicy.class, userPolicy.getUserPolicyId())).isNull();
        Simulation kept = entityManager.find(Simulation.class, simulation.getSimulationId());
        assertThat(kept).isNotNull();
        assertThat(kept.getUserPolicy()).isNull();
        // 실행 당시 계산한 정책 반영 결과는 그대로 남는다
        assertThat(kept.getPolicyCompletionDate()).isEqualTo(withPolicy);
        assertThat(kept.getPolicySuccessProbability()).isEqualTo(0.7);
    }
}
