package kr.fintarget.api.domain.simulation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import kr.fintarget.api.domain.goal.entity.Goal;
import kr.fintarget.api.domain.policy.dto.SimulationBenefitResponse;
import kr.fintarget.api.domain.simulation.entity.Simulation;
import kr.fintarget.api.domain.simulation.estimate.SavingEstimateResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시뮬레이션 응답의 추정 필드(#257)와 JSON 필드명. 프론트에 안내한 이름이 바뀌지 않도록 직렬화 결과를 확인한다.
 */
class SimulationResponseEstimateTest {

    // 앱 응답과 같은 설정으로 직렬화한다 (날짜 타입 지원)
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private static Simulation simulation(LocalDate policyDate) {
        Goal goal = new Goal(UUID.randomUUID(), "목표", 5_000_000L, 0L, LocalDate.now().plusMonths(12));
        return new Simulation(UUID.randomUUID(), goal, null, 300_000L, LocalDate.now().plusMonths(17),
                policyDate, LocalDate.now().plusMonths(15), LocalDate.now().plusMonths(20), 0.3,
                policyDate == null ? null : 0.9);
    }

    @Test
    void 기록_조회는_추정_필드가_항상_null이다() {
        // 저장 칸이 없어 정책 완료일이 있는 기록도 추정 여부를 알 수 없다
        SimulationResponse withPolicy = SimulationResponse.from(simulation(LocalDate.now().plusMonths(9)));
        SimulationResponse withoutPolicy = SimulationResponse.from(simulation(null));

        assertThat(withPolicy.policyBenefitEstimated()).isNull();
        assertThat(withPolicy.policyBenefitBasis()).isNull();
        assertThat(withoutPolicy.policyBenefitEstimated()).isNull();
        assertThat(withoutPolicy.policyBenefitBasis()).isNull();
    }

    @Test
    void 실행_응답은_추정_여부와_근거를_담는다() {
        SavingEstimateResult estimate = new SavingEstimateResult(310_000L, null, "근거 문구");

        SimulationResponse estimated = SimulationResponse.fromRun(simulation(LocalDate.now().plusMonths(9)), estimate);
        assertThat(estimated.policyBenefitEstimated()).isTrue();
        assertThat(estimated.policyBenefitBasis()).isEqualTo("근거 문구");

        SimulationResponse actual = SimulationResponse.fromRun(simulation(LocalDate.now().plusMonths(9)), null);
        assertThat(actual.policyBenefitEstimated()).isFalse();
        assertThat(actual.policyBenefitBasis()).isNull();

        SimulationResponse noPolicy = SimulationResponse.fromRun(simulation(null), null);
        assertThat(noPolicy.policyBenefitEstimated()).isNull();
        assertThat(noPolicy.policyBenefitBasis()).isNull();
    }

    @Test
    void 시뮬레이션_응답_JSON_필드명이_유지된다() throws Exception {
        JsonNode json = MAPPER.valueToTree(SimulationResponse.fromRun(simulation(LocalDate.now().plusMonths(9)),
                new SavingEstimateResult(310_000L, null, "근거")));

        assertThat(json.has("policyBenefitEstimated")).isTrue();
        assertThat(json.has("policyBenefitBasis")).isTrue();
        assertThat(json.get("policyBenefitEstimated").asBoolean()).isTrue();
        assertThat(json.get("policyBenefitBasis").asText()).isEqualTo("근거");
        // 기존 필드는 그대로다
        assertThat(json.has("policyCompletionDate")).isTrue();
        assertThat(json.has("policySuccessProbability")).isTrue();
        assertThat(json.has("expectedCompletionDate")).isTrue();
    }

    @Test
    void 시뮬레이션_혜택_JSON_필드명이_유지된다() {
        JsonNode estimated = MAPPER.valueToTree(SimulationBenefitResponse.fromEstimate(
                new SavingEstimateResult(470_000L, null, "근거 문구")));

        assertThat(estimated.get("estimated").asBoolean()).isTrue();
        assertThat(estimated.get("basis").asText()).isEqualTo("근거 문구");
        assertThat(estimated.get("monthlyAmount").asLong()).isEqualTo(470_000L);
        assertThat(estimated.get("months").asInt()).isEqualTo(12);
        assertThat(estimated.get("monthsSpecified").asBoolean()).isFalse();
        assertThat(estimated.get("oneTimeAmount").isNull()).isTrue();
        assertThat(estimated.get("maximum").asBoolean()).isFalse();
    }
}
