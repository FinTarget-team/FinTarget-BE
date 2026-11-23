package kr.fintarget.api.domain.policy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import kr.fintarget.api.domain.policy.condition.BenefitKind;
import kr.fintarget.api.domain.policy.condition.HousingRequirement;
import kr.fintarget.api.domain.policy.condition.IncomeBasis;
import kr.fintarget.api.domain.policy.condition.TargetType;
import kr.fintarget.api.domain.user.code.Region;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 정책 원문에서 AI로 추출한 신청 조건 (#220). 정책 추천에서 확실히 해당하지 않는 정책을 빼고 정렬하는 데만 쓴다.
 * 모든 컬럼은 nullable이고, 값이 없으면 UNKNOWN으로 취급한다 (조건을 모르면 제외하지 않음).
 *
 * <p>원문 해시와 프롬프트 버전을 같이 남겨, 원문이 바뀌지 않은 정책을 매일 동기화 때마다 다시 추출하지 않는다
 * ({@link PolicyBenefit}과 같은 방식). 정책 목록 API 응답에는 넣지 않는다.
 */
@Embeddable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PolicyCondition {

    @Enumerated(EnumType.STRING)
    @Column(name = "income_basis", length = 20)
    private IncomeBasis incomeBasis;

    // 본인 소득 상한(세전 월, 원). incomeBasis가 INDIVIDUAL일 때만 값이 있다. 연소득 기준이면 12로 나눈 값
    @Column(name = "income_max_monthly")
    private Long incomeMaxMonthly;

    @Enumerated(EnumType.STRING)
    @Column(name = "housing_requirement", length = 20)
    private HousingRequirement housingRequirement;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 20)
    private TargetType targetType;

    // 개인이 받는 경제적 혜택 유형 (#225, policy-condition-v2부터). 추천 상위 N 선택에만 쓴다. null은 UNKNOWN 취급
    @Enumerated(EnumType.STRING)
    @Column(name = "benefit_kind", length = 30)
    private BenefitKind benefitKind;

    // 신청 자격이 특정 시·군·구 거주자로 한정되는지 (#225, policy-condition-v2부터). 시·도 전체·전국이면 false, 알 수 없으면 null.
    // 온보딩 지역이 시·도 단위라 다른 구·군 한정 정책을 걸러낼 수 없어, 추천 상위 N 정렬에서 뒤로만 보낸다
    @Column(name = "local_scope")
    private Boolean localScope;

    // 원문에서 읽은 신청 가능 지역 (Region 코드 쉼표 구분). 기존 region이 빈 정책의 지역 보완에만 쓴다
    @Column(name = "extracted_region")
    private String extractedRegion;

    // 추출에 쓴 프롬프트 버전. 버전이 바뀌면 다시 추출한다
    @Column(name = "condition_prompt_version", length = 40)
    private String promptVersion;

    // 추출에 쓴 원문의 SHA-256. 원문이 바뀌면 다시 추출한다
    @Column(name = "condition_source_hash", length = 64)
    private String sourceHash;

    @Column(name = "condition_extracted_at")
    private LocalDateTime extractedAt;

    /** 이 원문·프롬프트 버전으로 이미 추출했는지. 아니면 다시 추출해야 한다 */
    public boolean isExtractedFrom(String sourceHash, String promptVersion) {
        return sourceHash != null && sourceHash.equals(this.sourceHash)
                && promptVersion != null && promptVersion.equals(this.promptVersion);
    }

    /**
     * 추출한 지역 코드 목록. 비어 있거나 Region 코드가 아닌 값이 하나라도 있으면 빈 목록(지역 보완 안 함).
     * 17개 시·도를 모두 담은 값도 전국과 같으므로 빈 목록으로 본다 (정렬에서 모든 사용자에게 "지역 정책"으로 올라가지 않게)
     */
    public List<String> extractedRegionCodes() {
        if (extractedRegion == null || extractedRegion.isBlank()) {
            return List.of();
        }
        List<String> codes = Arrays.stream(extractedRegion.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .distinct()
                .toList();
        if (codes.size() >= Region.values().length || !codes.stream().allMatch(code -> Region.fromCode(code) != null)) {
            return List.of();
        }
        return codes;
    }

    /** 소득·주택·대상 중 확인되지 않은(UNKNOWN 또는 null) 조건 수. 정렬에서 조건이 확인된 정책을 앞에 둔다 */
    public int unknownCount() {
        int count = 0;
        if (incomeBasis == null || incomeBasis == IncomeBasis.UNKNOWN) {
            count++;
        }
        if (housingRequirement == null || housingRequirement == HousingRequirement.UNKNOWN) {
            count++;
        }
        if (targetType == null || targetType == TargetType.UNKNOWN) {
            count++;
        }
        return count;
    }

    /** 경제적 혜택 유형. 추출 전(v1로 추출했거나 아직 추출하지 않음)이면 UNKNOWN */
    public BenefitKind benefitKindOrUnknown() {
        return benefitKind == null ? BenefitKind.UNKNOWN : benefitKind;
    }

    /** 조건 3개(소득·주택·대상)를 모두 모를 때의 unknownCount */
    public static final int MAX_UNKNOWN_COUNT = 3;
}
