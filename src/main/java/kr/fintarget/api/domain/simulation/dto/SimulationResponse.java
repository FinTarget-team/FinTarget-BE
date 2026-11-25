package kr.fintarget.api.domain.simulation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.fintarget.api.domain.simulation.entity.Simulation;
import kr.fintarget.api.domain.simulation.estimate.SavingEstimateResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record SimulationResponse(
    @Schema(description = "시뮬레이션 ID (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID simulationId,
    @Schema(description = "시뮬레이션 대상 목표 ID (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    UUID goalId,
    @Schema(description = "실행 당시 목표 제목. 목표를 나중에 수정해도 바뀌지 않습니다. 이 필드가 추가되기 전(#186)에 실행한 기록은 null입니다.",
            example = "내 집 마련", types = {"string", "null"})
    String goalTitle,
    @Schema(description = "실행 당시 목표 금액(원). 이 필드가 추가되기 전(#186)에 실행한 기록은 null입니다.",
            example = "50000000", types = {"integer", "null"})
    Long goalTargetAmount,
    @Schema(description = "실행 당시 현재까지 모은 금액(원). 이 필드가 추가되기 전(#186)에 실행한 기록은 null입니다.",
            example = "8000000", types = {"integer", "null"})
    Long goalCurrentAmount,
    @Schema(description = "실행 당시 목표 마감일 (yyyy-MM-dd). 이 필드가 추가되기 전(#186)에 실행한 기록은 null입니다.",
            example = "2028-12-31", types = {"string", "null"})
    LocalDate goalDeadline,
    @Schema(description = "요청에 사용된 월 저축액(원)", example = "500000")
    Long monthlySaving,
    @Schema(description = "몬테카를로 시뮬레이션 중앙값(50th percentile) 기준 예상 달성일", example = "2028-03-15")
    LocalDate expectedCompletionDate,
    @Schema(
            description = "정책 혜택을 반영한 예상 달성일(중앙값). 지원 기간과 1회 지급을 반영해 한 달씩 계산합니다. "
                    + "요청에 userPolicyId를 보내지 않았거나, 정책에 금액으로 환산된 혜택이 없으면(대출·현물·서비스 정책, 혜택 금액이 아직 정리되지 않은 정책) null입니다.",
            example = "2027-11-20",
            types = {"string", "null"}
    )
    LocalDate policyCompletionDate,
    @Schema(description = "낙관적 시나리오(10th percentile) 기준 예상 달성일", example = "2027-09-10")
    LocalDate optimisticCompletionDate,
    @Schema(description = "비관적 시나리오(90th percentile) 기준 예상 달성일", example = "2028-08-01")
    LocalDate pessimisticCompletionDate,
    @Schema(description = "목표 마감일까지 목표 금액을 달성할 확률(0.0~1.0). 1만 회 몬테카를로 시뮬레이션 기준. 정책 혜택은 반영하지 않은 값입니다.", example = "0.73")
    Double successProbability,
    @Schema(
            description = "정책 혜택을 반영한 달성 확률(0.0~1.0). policyCompletionDate와 같은 조건에서 계산되므로, "
                    + "policyCompletionDate가 null이면 이 값도 null입니다.",
            example = "0.91",
            types = {"number", "null"}
    )
    Double policySuccessProbability,
    @Schema(description = "시뮬레이션 실행(저장) 시각", example = "2026-08-19T10:00:00")
    LocalDateTime createdAt,
    @Schema(
            description = "정책 반영 계산에 추정 절감액을 썼는지 (#257). true면 실제 지급액이 아니라 주거비 절감 추정값으로 "
                    + "policyCompletionDate를 계산했으므로 화면에 \"예상\"임을 표시해야 합니다. 실제 지급액으로 계산했으면 false, "
                    + "정책 반영 계산을 하지 않았으면(userPolicyId 없음, 반영할 혜택·추정이 없음) null입니다. "
                    + "기록 조회(목록·목표별)에는 저장하지 않아 항상 null입니다.",
            example = "true",
            types = {"boolean", "null"}
    )
    Boolean policyBenefitEstimated,
    @Schema(
            description = "추정 근거 문구 (#257). policyBenefitEstimated가 true일 때만 값이 있고, 아니면 null입니다.",
            example = "입주 시 서울 주요 대학가 원룸 평균 월세 62만원의 50% 수준 임대료로 계산한 예상 절감액",
            types = {"string", "null"}
    )
    String policyBenefitBasis
) {
    /**
     * 저장된 기록. 추정 여부는 저장하는 칸이 없어 추정 필드 2개가 항상 null이다 (#257).
     * 실행 응답은 {@link #fromRun(Simulation, SavingEstimateResult)}를 쓴다.
     */
    public static SimulationResponse from(Simulation simulation) {
        return create(simulation, null, null);
    }

    /**
     * 실행 결과. 정책 반영을 계산했으면 추정값으로 계산했는지와 그 근거를 함께 내려준다 (#257).
     *
     * @param estimate 추정으로 계산했을 때의 결과. 실제 지급액으로 계산했거나 정책 반영을 하지 않았으면 null
     */
    public static SimulationResponse fromRun(Simulation simulation, SavingEstimateResult estimate) {
        // 정책 반영 계산을 하지 않았으면(혜택도 추정도 없음) 추정 여부를 알릴 것이 없어 null
        Boolean estimated = simulation.getPolicyCompletionDate() == null ? null : estimate != null;
        return create(simulation, estimated, estimate == null ? null : estimate.basis());
    }

    private static SimulationResponse create(Simulation simulation, Boolean policyBenefitEstimated, String policyBenefitBasis) {
        return new SimulationResponse(
            simulation.getSimulationId(),
            simulation.getGoal().getGoalId(),
            simulation.getGoalTitle(),
            simulation.getGoalTargetAmount(),
            simulation.getGoalCurrentAmount(),
            simulation.getGoalDeadline(),
            simulation.getMonthlySaving(),
            simulation.getExpectedCompletionDate(),
            simulation.getPolicyCompletionDate(),
            simulation.getOptimisticCompletionDate(),
            simulation.getPessimisticCompletionDate(),
            simulation.getSuccessProbability(),
            simulation.getPolicySuccessProbability(),
            simulation.getCreatedAt(),
            policyBenefitEstimated,
            policyBenefitBasis
        );
    }
}
