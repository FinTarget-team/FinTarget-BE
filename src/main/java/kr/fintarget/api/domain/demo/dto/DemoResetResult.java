package kr.fintarget.api.domain.demo.dto;

/**
 * 시연 초기화 결과 (#239). 지운 항목별 건수와, 지우지 않고 남긴 지출 건수.
 *
 * @param deleted      지운 항목별 건수
 * @param keptExpenses 남긴 지출 건수 (mock-bank 거래를 시연마다 다시 만들 수 없어 지출은 지우지 않는다)
 */
public record DemoResetResult(DemoResetCounts deleted, long keptExpenses) {
}
