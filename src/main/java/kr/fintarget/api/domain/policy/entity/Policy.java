package kr.fintarget.api.domain.policy.entity;

import jakarta.persistence.*;
import kr.fintarget.api.domain.policy.benefit.BenefitType;
import kr.fintarget.api.domain.user.code.Region;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Entity
@Table(name = "policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "policy_id", columnDefinition = "uuid")
    private UUID policyId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    @Column(name = "income_limit")
    private Long incomeLimit;

    @Column(name = "benefit_amount")
    private Long benefitAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false)
    private PolicyType policyType;

    @Column(name = "region")
    private String region;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "source")
    private String source;

    // 정책 원문 (#113). 원문이 없는 정책(테스트 데이터, 원문 저장 전 행)은 null일 수 있다
    @Embedded
    private PolicyText text;

    // 시뮬레이션에 반영할 현금 지원 혜택 (#163). 아직 채워지지 않은 정책은 null이다
    @Embedded
    private PolicyBenefit benefit;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static Policy create(String name, String description, Integer minAge, Integer maxAge,
                                Long incomeLimit, Long benefitAmount, PolicyType policyType, String region,
                                String externalId, String source) {
        return create(name, description, minAge, maxAge, incomeLimit, benefitAmount, policyType, region,
                externalId, source, null);
    }

    public static Policy create(String name, String description, Integer minAge, Integer maxAge,
                                Long incomeLimit, Long benefitAmount, PolicyType policyType, String region,
                                String externalId, String source, PolicyText text) {
        Policy policy = new Policy();
        policy.text = text;
        policy.name = name;
        policy.description = description;
        policy.minAge = minAge;
        policy.maxAge = maxAge;
        policy.incomeLimit = incomeLimit;
        policy.benefitAmount = benefitAmount;
        policy.policyType = policyType;
        policy.region = region;
        policy.externalId = externalId;
        policy.source = source;
        return policy;
    }

    /**
     * region(쉼표로 구분된 Region 코드)을 코드 목록으로 돌려준다. 빈 목록은 전국(지역 제한 없음)이다.
     * 토큰은 앞뒤 공백을 제거해 비교하고 중복은 제거하며, 빈 토큰은 건너뛴다.
     * Region 코드가 아닌 토큰(매퍼 변환 전 값 등)이 하나라도 있으면 정책 전체를 전국으로 보고 WARN 로그를 남긴다.
     * 해당 토큰만 버리면 그 지역 사용자가 잘못 제외되므로(예: "서울,BUSAN"에서 서울 사용자), 잘못 제외보다 잘못 통과를 택하고
     * 매퍼가 매핑에 실패한 지역을 null=전국으로 저장하는 규칙과 맞춘다.
     */
    public List<String> regionCodes() {
        if (region == null || region.isBlank()) {
            return List.of();
        }
        List<String> codes = Arrays.stream(region.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .distinct()
                .toList();
        if (codes.stream().anyMatch(code -> Region.fromCode(code) == null)) {
            log.warn("정책 지역 값에 Region 코드가 아닌 값이 있어 전국으로 처리: policyId={}, externalId={}, region={}",
                    policyId, externalId, region);
            return List.of();
        }
        return codes;
    }

    /**
     * 사용자 거주 지역에서 신청할 수 있는 정책인지. 전국 정책은 항상 true,
     * 지역 정책은 사용자 지역 코드가 정책 지역 코드 중 하나와 정확히 같을 때만 true.
     * 사용자 지역이 없으면(null/빈 값) 전국 정책만 true다.
     */
    public boolean appliesToRegion(String userRegion) {
        List<String> codes = regionCodes();
        if (codes.isEmpty()) {
            return true;
        }
        if (userRegion == null || userRegion.isBlank()) {
            return false;
        }
        return codes.contains(userRegion.trim());
    }

    /**
     * 시뮬레이션에 반영할 현금 지원 혜택이 있는지 (#163).
     * 혜택이 아직 채워지지 않은 정책(benefit=null)과 금액으로 환산되지 않는 정책을 함께 걸러낸다.
     */
    public boolean hasBenefitForSimulation() {
        return benefit != null && benefit.hasAny();
    }

    /** 대출 상품인지 (#201). 혜택 추출(#162)이 LOAN으로 분류한 정책이며, 아직 추출되지 않은 정책(benefit=null)은 false다 */
    public boolean isLoanProduct() {
        return benefit != null && benefit.getType() == BenefitType.LOAN;
    }

    // 원문에서 추출·정리한 혜택 금액을 채운다 (#163 입력 형식, #162 추출 작업에서 호출)
    public void updateBenefit(PolicyBenefit benefit) {
        this.benefit = benefit;
    }

    /** 이 원문·프롬프트 버전으로 아직 혜택을 추출하지 않았는지 (#162). 처음이거나 원문·버전이 바뀌면 true */
    public boolean needsBenefitExtraction(String sourceHash, String promptVersion) {
        return benefit == null || !benefit.isExtractedFrom(sourceHash, promptVersion);
    }

    /**
     * 외부 API에서 다시 받아온 값으로 내용을 갱신한다 (externalId/source는 식별자라 유지).
     * benefit은 소스 응답이 아니라 원문 추출로 채우는 값이라 동기화가 덮지 않는다 — 덮으면 매일 03:00
     * 재동기화마다 추출 결과가 사라진다. 혜택 갱신은 updateBenefit으로만 한다 (#163)
     */
    public void updateFrom(Policy fetched) {
        this.name = fetched.name;
        this.description = fetched.description;
        this.minAge = fetched.minAge;
        this.maxAge = fetched.maxAge;
        this.incomeLimit = fetched.incomeLimit;
        this.benefitAmount = fetched.benefitAmount;
        this.policyType = fetched.policyType;
        this.region = fetched.region;
        this.text = fetched.text;
    }
}