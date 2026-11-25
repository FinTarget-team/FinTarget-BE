package kr.fintarget.api.domain.policy.manual;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 직접 등록 정책 동기화 결과 (#245) */
public record ManualPolicySyncResult(
        @Schema(description = "정책별 등록 결과")
        List<Item> policies,
        @Schema(description = "동기화 정책 혜택 보정 결과. found=false면 이 환경에 그 외부 ID의 정책이 없어 적용하지 않음")
        List<OverrideItem> benefitOverrides,
        @Schema(description = "동기화 정책 원문(신청 기간·신청 방법·기타 참고 사항) 보정 결과. found=false거나 nameMatches=false면 적용하지 않음")
        List<TextOverrideItem> textOverrides,
        @Schema(description = "추천 목록 고정 노출 대상 (목록 순서 = 노출 순서). found·nameMatches가 false면 이 환경에서는 고정하지 않음")
        List<PinItem> pins,
        @Schema(description = "시뮬레이션 월 절감액 추정 대상 (#257). found·nameMatches가 false면 이 환경에서는 추정하지 않음. "
                + "추정은 DB에 쓰지 않으므로 이 항목은 외부 ID 확인용이고, 동기화를 해야 적용되는 것은 아님")
        List<SavingEstimateItem> savingEstimates,
        @Schema(description = "단계별 결과 (textOverride, chunk, benefit, condition, benefitOverride, rerank, embedding). "
                + "다른 작업과 겹쳐 실행하지 못한 단계는 \"skipped: 사유\"", example = "{\"benefitOverride\": 0, \"rerank\": \"requested\"}")
        Map<String, Object> steps,
        @Schema(description = "소요 시간(ms)", example = "45000")
        long elapsedMs
) {

    public record Item(
            UUID policyId,
            @Schema(example = "MANUAL-HOPE-DOUBLE-2026") String externalId,
            @Schema(example = "2026 희망두배 청년통장") String name,
            @Schema(description = "CREATED(새로 등록) / UPDATED(이미 있어 리소스 내용으로 갱신)", example = "CREATED") String status,
            @Schema(description = "검색용 청크 수", example = "3") int chunkCount,
            Integer minAge,
            Integer maxAge,
            @Schema(description = "신청 가능 지역 코드. 빈 배열이면 전국", example = "[\"SEOUL\"]") List<String> regions,
            @Schema(description = "조건 추출: 소득 기준 (아직 추출 전이면 null)", example = "INDIVIDUAL") String incomeBasis,
            @Schema(description = "조건 추출: 본인 월 소득 상한(원)", example = "2550000") Long incomeMaxMonthly,
            @Schema(description = "조건 추출: 특정 대상 전용 여부", example = "false") Boolean specialTarget,
            @Schema(description = "조건 추출: 특정 대상 집단", example = "근로 청년") String specialTargetGroup,
            @Schema(description = "조건 추출: 경제적 혜택 유형", example = "MONEY") String benefitKind,
            @Schema(description = "시뮬레이션 혜택 유형 (리소스 값)", example = "CASH") String benefitType,
            Long monthlyAmount,
            Integer months,
            Long oneTimeAmount,
            @Schema(description = "시뮬레이션에 반영되는지", example = "true") boolean simulationReflected
    ) {
    }

    public record SavingEstimateItem(
            @Schema(example = "YOUTH_CENTER") String source,
            @Schema(example = "20260922005400113506") String externalId,
            @Schema(example = "기숙사형 청년주택 공급") String name,
            @Schema(description = "추정 규칙", example = "RENT_RATIO") String rule,
            @Schema(description = "추정을 적용하는 사용자 지역. 빈 배열이면 전 지역", example = "[\"SEOUL\"]") List<String> userRegions,
            @Schema(description = "이 환경에 대상 정책이 있는지") boolean found,
            @Schema(description = "저장된 정책명이 리소스와 같은지. 다르면 추정하지 않음") boolean nameMatches
    ) {
    }

    public record PinItem(
            @Schema(example = "YOUTH_CENTER") String source,
            @Schema(example = "20261021005400214208") String externalId,
            @Schema(example = "2026 청년 월세 지원") String name,
            @Schema(description = "고정 노출할 직업 유형", example = "[\"STUDENT\", \"EMPLOYED\", \"UNEMPLOYED\"]") List<String> recommendFor,
            @Schema(description = "이 환경에 대상 정책이 있는지") boolean found,
            @Schema(description = "저장된 정책명이 리소스와 같은지. 다르면 고정하지 않음") boolean nameMatches
    ) {
    }

    public record TextOverrideItem(
            @Schema(example = "YOUTH_CENTER") String source,
            @Schema(example = "20261021005400214208") String externalId,
            @Schema(example = "2026 청년 월세 지원") String name,
            @Schema(description = "이 환경에 대상 정책이 있는지") boolean found,
            @Schema(description = "저장된 정책명이 리소스와 같은지. 다르면 보정하지 않음") boolean nameMatches,
            @Schema(description = "저장된 원문에 보정이 들어가 있는지") boolean applied,
            @Schema(description = "저장된 신청 기간", example = "20260506 ~ 20260519") String applyPeriod
    ) {
    }

    public record OverrideItem(
            @Schema(example = "YOUTH_CENTER") String source,
            @Schema(example = "20261021005400214208") String externalId,
            @Schema(example = "2026 청년 월세 지원") String name,
            @Schema(description = "이 환경에 대상 정책이 있는지") boolean found,
            @Schema(description = "저장된 정책명이 리소스와 같은지. 다르면 덮지 않음") boolean nameMatches,
            @Schema(description = "저장된 시뮬레이션 혜택 유형", example = "CASH") String benefitType,
            Long monthlyAmount,
            Integer months,
            Long oneTimeAmount,
            @Schema(description = "시뮬레이션에 반영되는지", example = "true") boolean simulationReflected
    ) {
    }
}
