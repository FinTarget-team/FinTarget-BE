package kr.fintarget.api.domain.policy.manual;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.fintarget.api.domain.policy.benefit.BenefitType;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyBenefit;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.user.code.EmploymentType;
import kr.fintarget.api.domain.user.code.Region;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 공공 API로 수집되지 않아 직접 등록하는 정책 목록 (#245). 본문은 src/main/resources/policy/manual-policies.json에 둔다.
 *
 * <p>출처(source)를 {@link #SOURCE}로 저장해, 온통청년·KINFA 동기화의 삭제·갱신 대상(같은 출처만)에서 빠진다
 * ({@link kr.fintarget.api.domain.policy.service.PolicySyncWriter}).
 * 시뮬레이션 혜택 값(benefit)은 AI 혜택 추출보다 리소스 값을 우선한다 ({@link #applyBenefit}).
 *
 * <p>고정 노출(pins): 출처·외부 정책 ID·정책명으로 지정한 정책을 목록 순서대로, recommendFor에 사용자의 직업 유형이 있으면
 * 추천 목록 맨 앞에 고정 노출한다 ({@link ManualPolicyPinner}). 직접 등록 정책과 동기화 정책을 함께 지정할 수 있다.
 *
 * <p>원문 보정(textOverrides): 동기화 정책 중 신청 기간이 실제 접수 기간과 다른 정책 등은 출처·외부 정책 ID·정책명으로 지정해
 * 신청 기간을 바꾸거나 신청 방법·기타 참고 사항에 문장을 덧붙인다. 공식 출처(sourceUrl)에 있는 내용만 넣는다.
 * 동기화가 원문을 되돌리므로 동기화 직후와 직접 등록 정책 동기화 때 다시 적용한다 ({@link ManualPolicyTextCorrector}).
 *
 * <p>혜택 보정(benefitOverrides): 온통청년처럼 동기화되는 정책 중 혜택 분류가 틀린 정책은 출처와 외부 정책 ID로 지정해
 * 시뮬레이션 혜택 값만 리소스 값으로 덮는다. 정책 원문·조건은 동기화 결과를 그대로 쓴다.
 * 동기화는 혜택을 건드리지 않고(Policy.updateFrom), 원문이 바뀌어 혜택을 다시 추출해도 저장 직전에 리소스 값으로 덮으므로 유지된다.
 * 외부 ID가 다른 정책에 다시 쓰이는 경우를 막으려고 정책명이 같을 때만 덮는다.
 */
@Slf4j
@Component
public class ManualPolicyCatalog {

    public static final String SOURCE = "MANUAL";
    static final String RESOURCE = "policy/manual-policies.json";

    private final Map<String, ManualPolicy> byExternalId;
    private final List<ManualPolicy> ordered;
    private final Map<String, BenefitOverride> overridesByKey;
    private final List<BenefitOverride> overrides;
    private final List<TextOverride> textOverrides;
    private final List<Pin> pins;
    private final List<SavingEstimate> savingEstimates;

    public ManualPolicyCatalog() {
        this(load(RESOURCE));
    }

    private ManualPolicyCatalog(Document document) {
        this(document.policies(), orEmpty(document.benefitOverrides()), orEmpty(document.textOverrides()),
                orEmpty(document.pins()), orEmpty(document.savingEstimates()));
    }

    ManualPolicyCatalog(List<ManualPolicy> policies) {
        this(policies, List.of());
    }

    ManualPolicyCatalog(List<ManualPolicy> policies, List<BenefitOverride> benefitOverrides) {
        this(policies, benefitOverrides, List.of(), List.of());
    }

    ManualPolicyCatalog(List<ManualPolicy> policies, List<BenefitOverride> benefitOverrides, List<TextOverride> textOverrides) {
        this(policies, benefitOverrides, textOverrides, List.of());
    }

    ManualPolicyCatalog(List<ManualPolicy> policies, List<BenefitOverride> benefitOverrides,
                        List<TextOverride> textOverrides, List<Pin> pins) {
        this(policies, benefitOverrides, textOverrides, pins, List.of());
    }

    ManualPolicyCatalog(List<ManualPolicy> policies, List<BenefitOverride> benefitOverrides,
                        List<TextOverride> textOverrides, List<Pin> pins, List<SavingEstimate> savingEstimates) {
        Map<String, ManualPolicy> map = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        for (ManualPolicy policy : policies) {
            policy.validate();
            if (map.put(policy.externalId(), policy) != null) {
                throw new IllegalStateException("직접 등록 정책 externalId가 겹칩니다: " + policy.externalId());
            }
            if (!names.add(policy.name())) {
                throw new IllegalStateException("직접 등록 정책 이름이 겹칩니다: " + policy.name());
            }
        }
        this.byExternalId = Map.copyOf(map);
        this.ordered = List.copyOf(map.values());

        Map<String, BenefitOverride> overrideMap = new LinkedHashMap<>();
        for (BenefitOverride override : benefitOverrides) {
            override.validate();
            if (overrideMap.put(key(override.source(), override.externalId()), override) != null) {
                throw new IllegalStateException("혜택 보정 대상이 겹칩니다: " + override.source() + " " + override.externalId());
            }
        }
        this.overridesByKey = Map.copyOf(overrideMap);
        this.overrides = List.copyOf(overrideMap.values());

        Set<String> textKeys = new HashSet<>();
        for (TextOverride override : textOverrides) {
            override.validate();
            if (!textKeys.add(key(override.source(), override.externalId()))) {
                throw new IllegalStateException("원문 보정 대상이 겹칩니다: " + override.source() + " " + override.externalId());
            }
        }
        this.textOverrides = List.copyOf(textOverrides);

        Set<String> pinKeys = new HashSet<>();
        for (Pin pin : pins) {
            pin.validate();
            if (!pinKeys.add(key(pin.source(), pin.externalId()))) {
                throw new IllegalStateException("고정 노출 대상이 겹칩니다: " + pin.source() + " " + pin.externalId());
            }
            if (SOURCE.equals(pin.source())) {
                ManualPolicy manual = map.get(pin.externalId());
                if (manual == null || !manual.name().equals(pin.name())) {
                    throw new IllegalStateException("고정 노출 대상이 직접 등록 정책에 없거나 이름이 다릅니다: " + pin.externalId());
                }
            }
        }
        this.pins = List.copyOf(pins);

        Set<String> estimateKeys = new HashSet<>();
        for (SavingEstimate estimate : savingEstimates) {
            estimate.validate();
            if (!estimateKeys.add(key(estimate.source(), estimate.externalId()))) {
                throw new IllegalStateException("절감액 추정 대상이 겹칩니다: " + estimate.source() + " " + estimate.externalId());
            }
        }
        this.savingEstimates = List.copyOf(savingEstimates);
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String key(String source, String externalId) {
        return source + "|" + externalId;
    }

    public List<ManualPolicy> policies() {
        return ordered;
    }

    /** 동기화 정책 혜택 보정 목록 */
    public List<BenefitOverride> benefitOverrides() {
        return overrides;
    }

    /** 동기화 정책 원문 보정 목록 */
    public List<TextOverride> textOverrides() {
        return textOverrides;
    }

    /** 추천 목록 고정 노출 대상 (목록 순서 = 노출 순서) */
    public List<Pin> pins() {
        return pins;
    }

    /** 시뮬레이션 월 절감액 추정 대상 (#257) */
    public List<SavingEstimate> savingEstimates() {
        return savingEstimates;
    }

    /** 직접 등록 정책이면 리소스 항목. 다른 출처 정책이면 빈 값 */
    public Optional<ManualPolicy> find(Policy policy) {
        if (policy == null || !SOURCE.equals(policy.getSource()) || policy.getExternalId() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byExternalId.get(policy.getExternalId()));
    }

    /**
     * 직접 등록 정책이면 시뮬레이션 혜택 값(유형·월 금액·개월·1회 금액·최대 기준·근거)을 리소스 값으로 바꾼다.
     * 추출 메타데이터(프롬프트 버전·원문 해시·추출 시각)는 extracted 값을 그대로 둬서, 원문이 같으면 다음 동기화 때 다시 추출하지 않는다.
     * 혜택 보정 대상(출처·외부 ID·정책명이 같은 동기화 정책)도 같다. 그 밖의 정책이면 extracted를 그대로 돌려준다.
     */
    public PolicyBenefit applyBenefit(Policy policy, PolicyBenefit extracted) {
        return resourceBenefit(policy)
                .map(resource -> resource.toPolicyBenefit(
                        extracted == null ? null : extracted.getPromptVersion(),
                        extracted == null ? null : extracted.getSourceHash(),
                        extracted == null ? null : extracted.getExtractedAt()))
                .orElse(extracted);
    }

    /** 리소스에 시뮬레이션 혜택 값이 있는 정책이면 그 값 (직접 등록 정책 또는 혜택 보정 대상) */
    public Optional<ManualBenefit> resourceBenefit(Policy policy) {
        Optional<ManualPolicy> manual = find(policy);
        if (manual.isPresent()) {
            return Optional.of(manual.get().benefit());
        }
        if (policy == null || policy.getSource() == null || policy.getExternalId() == null) {
            return Optional.empty();
        }
        BenefitOverride override = overridesByKey.get(key(policy.getSource(), policy.getExternalId()));
        if (override == null) {
            return Optional.empty();
        }
        if (!override.name().equals(policy.getName())) {
            log.warn("혜택 보정 대상의 정책명이 달라 덮지 않음 externalId={} 리소스={} 저장={}",
                    override.externalId(), override.name(), policy.getName());
            return Optional.empty();
        }
        return Optional.of(override.benefit());
    }

    private static Document load(String path) {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return mapper.readValue(in, Document.class);
        } catch (IOException e) {
            throw new UncheckedIOException("직접 등록 정책 파일을 읽을 수 없습니다: " + path, e);
        }
    }

    record Document(String description, List<ManualPolicy> policies, List<BenefitOverride> benefitOverrides,
                    List<TextOverride> textOverrides, List<Pin> pins, List<SavingEstimate> savingEstimates) {
    }

    private static boolean sameTarget(String source, String externalId, String name, Policy policy) {
        return policy != null && source.equals(policy.getSource()) && externalId.equals(policy.getExternalId())
                && name.equals(policy.getName());
    }

    /**
     * 추천 목록 고정 노출 대상 1건. 출처·외부 정책 ID·정책명이 모두 같은 정책만 고정한다 (다른 환경에서 정책번호가 다르면 고정하지 않음).
     *
     * @param recommendFor 고정 노출할 직업 유형 (EmploymentType 코드)
     */
    public record Pin(String source, String externalId, String name, List<String> recommendFor) {

        public Pin {
            recommendFor = recommendFor == null ? List.of() : List.copyOf(recommendFor);
        }

        public boolean matches(Policy policy) {
            return sameTarget(source, externalId, name, policy);
        }

        public boolean recommendedFor(EmploymentType employmentType) {
            return employmentType != null && recommendFor.contains(employmentType.name());
        }

        void validate() {
            if (source == null || source.isBlank() || externalId == null || externalId.isBlank()
                    || name == null || name.isBlank()) {
                throw new IllegalStateException("고정 노출 대상의 출처·외부 ID·정책명이 비었습니다: " + externalId);
            }
            if (recommendFor.isEmpty()) {
                throw new IllegalStateException("고정 노출 대상의 recommendFor가 비었습니다: " + externalId);
            }
            for (String type : recommendFor) {
                if (EmploymentType.fromCode(type) == null) {
                    throw new IllegalStateException("고정 노출 recommendFor 값이 직업 유형이 아닙니다: " + externalId + " " + type);
                }
            }
        }
    }

    /**
     * 동기화 정책 원문 보정 1건. 출처·외부 정책 ID·정책명이 모두 같을 때만 적용한다.
     *
     * @param applyPeriod       신청 기간을 이 값으로 바꾼다 (null이면 그대로). 추천 정렬의 신청 가능 판정도 이 값을 쓴다
     * @param applyMethodAppend 신청 방법 끝에 덧붙일 문장 (null이면 그대로)
     * @param etcNoteAppend     기타 참고 사항 끝에 덧붙일 문장 (null이면 그대로)
     * @param sourceUrl         보정 내용의 공식 출처 (기록용)
     */
    public record TextOverride(String source, String externalId, String name, String reason, String sourceUrl,
                               String applyPeriod, String applyMethodAppend, String etcNoteAppend) {

        public boolean matches(Policy policy) {
            return sameTarget(source, externalId, name, policy);
        }

        /** 보정한 원문. 이미 보정된 원문이면 같은 값 (덧붙인 문장이 이미 있으면 다시 붙이지 않음) */
        public PolicyText correct(PolicyText current) {
            PolicyText base = current == null ? PolicyText.builder().build() : current;
            return base.withCorrection(
                    applyPeriod == null ? base.getApplyPeriod() : applyPeriod,
                    appendOnce(base.getApplyMethod(), applyMethodAppend),
                    appendOnce(base.getEtcNote(), etcNoteAppend));
        }

        /** 이 원문에 보정이 이미 들어가 있는지 */
        public boolean isApplied(PolicyText current) {
            if (current == null) {
                return false;
            }
            PolicyText corrected = correct(current);
            return Objects.equals(current.getApplyPeriod(), corrected.getApplyPeriod())
                    && Objects.equals(current.getApplyMethod(), corrected.getApplyMethod())
                    && Objects.equals(current.getEtcNote(), corrected.getEtcNote());
        }

        private static String appendOnce(String base, String sentence) {
            if (sentence == null || sentence.isBlank()) {
                return base;
            }
            if (base == null || base.isBlank()) {
                return sentence;
            }
            return base.contains(sentence) ? base : base + "\n" + sentence;
        }

        void validate() {
            if (source == null || source.isBlank() || SOURCE.equals(source)) {
                throw new IllegalStateException("원문 보정 출처가 비었거나 MANUAL입니다: " + externalId);
            }
            if (externalId == null || externalId.isBlank() || name == null || name.isBlank()) {
                throw new IllegalStateException("원문 보정 외부 ID·정책명이 비었습니다: " + source);
            }
            if (sourceUrl == null || sourceUrl.isBlank()) {
                throw new IllegalStateException("원문 보정의 공식 출처가 비었습니다: " + externalId);
            }
            if (applyPeriod == null && applyMethodAppend == null && etcNoteAppend == null) {
                throw new IllegalStateException("원문 보정 값이 없습니다: " + externalId);
            }
        }
    }

    /**
     * 동기화 정책 혜택 보정 1건. 출처·외부 정책 ID로 정책을 찾고, 정책명이 같을 때만 혜택 값을 덮는다.
     *
     * @param reason 보정 이유와 원문 근거 (기록용)
     */
    public record BenefitOverride(String source, String externalId, String name, String reason, ManualBenefit benefit) {

        void validate() {
            if (source == null || source.isBlank() || SOURCE.equals(source)) {
                throw new IllegalStateException("혜택 보정 출처가 비었거나 MANUAL입니다: " + externalId);
            }
            if (externalId == null || externalId.isBlank() || name == null || name.isBlank()) {
                throw new IllegalStateException("혜택 보정 외부 ID·정책명이 비었습니다: " + source);
            }
            if (benefit == null) {
                throw new IllegalStateException("혜택 보정 값이 없습니다: " + externalId);
            }
            benefit.validate(externalId);
        }
    }

    /**
     * 시뮬레이션 월 절감액 추정 대상 1건 (#257). 금액이 없어(SERVICE) 시뮬레이션에 반영되지 않는 주거 정책을
     * 공식 기준값으로 추정하기 위한 규칙이다. 출처·외부 정책 ID·정책명이 모두 같은 정책에만 적용한다.
     *
     * <p>DB에 쓰지 않고 요청 시점에만 계산하므로, 추천 정렬에 쓰이는 {@link Policy#hasBenefitForSimulation()}은 바뀌지 않는다.
     *
     * @param rule         FIXED_RENT(기준 월세 − rent), RENT_RATIO(기준 월세 × (1 − ratio)),
     *                     INCOME_TIERED_RATIO(사용자 소득 구간의 tier 비율로 RENT_RATIO와 같이 계산)
     * @param rent         FIXED_RENT일 때 실제 임대료(원)
     * @param ratio        RENT_RATIO일 때 시세 대비 임대료 비율 (0 초과 1 미만)
     * @param tiers        INCOME_TIERED_RATIO일 때 소득 구간별 비율. 상한 오름차순이고 마지막은 상한 없음(null)
     * @param months       절감 개월 수. 원문에 거주 기간이 없으면 생략한다 (계산은 기본 12개월)
     * @param userRegions  이 추정을 적용할 사용자 지역 코드. 기준 월세가 특정 지역 값이라 그 지역 사용자에게만 적용한다
     * @param sourceUrl    기준값·비율의 공식 출처 (기록용)
     */
    public record SavingEstimate(String source, String externalId, String name, String rule, Long rent, Double ratio,
                                 List<Tier> tiers, Integer months, List<String> userRegions, String sourceUrl) {

        public static final String FIXED_RENT = "FIXED_RENT";
        public static final String RENT_RATIO = "RENT_RATIO";
        public static final String INCOME_TIERED_RATIO = "INCOME_TIERED_RATIO";

        public SavingEstimate {
            tiers = tiers == null ? List.of() : List.copyOf(tiers);
            userRegions = userRegions == null ? List.of() : List.copyOf(userRegions);
        }

        public boolean matches(Policy policy) {
            return sameTarget(source, externalId, name, policy);
        }

        /** 이 사용자 지역에 적용하는 추정인지. userRegions가 비어 있으면 전 지역 */
        public boolean appliesTo(String userRegion) {
            return userRegions.isEmpty() || (userRegion != null && userRegions.contains(userRegion));
        }

        /** 소득 구간 하한이 들어가는 첫 tier의 비율. INCOME_TIERED_RATIO가 아니거나 찾지 못하면 null */
        public Double ratioFor(long minIncome) {
            for (Tier tier : tiers) {
                if (tier.maxIncomeExclusive() == null || minIncome < tier.maxIncomeExclusive()) {
                    return tier.ratio();
                }
            }
            return null;
        }

        void validate() {
            if (source == null || source.isBlank() || SOURCE.equals(source)) {
                throw new IllegalStateException("절감액 추정 출처가 비었거나 MANUAL입니다: " + externalId);
            }
            if (externalId == null || externalId.isBlank() || name == null || name.isBlank()) {
                throw new IllegalStateException("절감액 추정 외부 ID·정책명이 비었습니다: " + source);
            }
            if (months != null && months < 1) {
                throw new IllegalStateException("절감액 추정 개월 수가 1보다 작습니다: " + externalId);
            }
            for (String region : userRegions) {
                if (Region.fromCode(region) == null) {
                    throw new IllegalStateException("절감액 추정 지역 코드가 아닙니다: " + externalId + " " + region);
                }
            }
            switch (rule == null ? "" : rule) {
                case FIXED_RENT -> validateFixedRent();
                case RENT_RATIO -> validateRentRatio();
                case INCOME_TIERED_RATIO -> validateTiers();
                default -> throw new IllegalStateException("절감액 추정 규칙이 아닙니다: " + externalId + " " + rule);
            }
        }

        private void validateFixedRent() {
            if (rent == null || rent <= 0) {
                throw new IllegalStateException("FIXED_RENT인데 임대료가 없습니다: " + externalId);
            }
            if (ratio != null || !tiers.isEmpty()) {
                throw new IllegalStateException("FIXED_RENT에 ratio·tiers를 함께 둘 수 없습니다: " + externalId);
            }
        }

        private void validateRentRatio() {
            if (ratio == null || ratio <= 0 || ratio >= 1) {
                throw new IllegalStateException("RENT_RATIO 비율은 0보다 크고 1보다 작아야 합니다: " + externalId + " " + ratio);
            }
            if (rent != null || !tiers.isEmpty()) {
                throw new IllegalStateException("RENT_RATIO에 rent·tiers를 함께 둘 수 없습니다: " + externalId);
            }
        }

        private void validateTiers() {
            if (tiers.isEmpty()) {
                throw new IllegalStateException("INCOME_TIERED_RATIO인데 tiers가 없습니다: " + externalId);
            }
            if (rent != null || ratio != null) {
                throw new IllegalStateException("INCOME_TIERED_RATIO에 rent·ratio를 함께 둘 수 없습니다: " + externalId);
            }
            long previous = Long.MIN_VALUE;
            for (int i = 0; i < tiers.size(); i++) {
                Tier tier = tiers.get(i);
                if (tier.ratio() == null || tier.ratio() <= 0 || tier.ratio() >= 1) {
                    throw new IllegalStateException("절감액 추정 tier 비율은 0보다 크고 1보다 작아야 합니다: "
                            + externalId + " " + tier.ratio());
                }
                boolean last = i == tiers.size() - 1;
                if (last != (tier.maxIncomeExclusive() == null)) {
                    throw new IllegalStateException("절감액 추정 tier는 마지막 하나만 상한이 없어야 합니다: " + externalId);
                }
                if (tier.maxIncomeExclusive() != null) {
                    if (tier.maxIncomeExclusive() <= previous) {
                        throw new IllegalStateException("절감액 추정 tier 상한이 오름차순이 아닙니다: " + externalId);
                    }
                    previous = tier.maxIncomeExclusive();
                }
            }
        }

        /** 소득 구간별 임대료 비율. maxIncomeExclusive 미만이면 이 비율 (마지막은 null = 상한 없음) */
        public record Tier(Long maxIncomeExclusive, Double ratio) {
        }
    }

    /** 직접 등록 정책 1건. 원문 필드는 온통청년 정책과 같은 칸에 넣어 청크·추출·채팅이 그대로 쓰게 한다 */
    public record ManualPolicy(String externalId, String name, String policyType, String region, Integer minAge, Integer maxAge,
                               String description, String supportContent, String eligibilityDetail, String incomeCondition,
                               String applyMethod, String applyPeriod, String applyUrl, String referenceUrl, String contactInfo,
                               String etcNote, ManualBenefit benefit) {

        void validate() {
            if (externalId == null || !externalId.startsWith("MANUAL-")) {
                throw new IllegalStateException("직접 등록 정책 externalId는 MANUAL-로 시작해야 합니다: " + externalId);
            }
            if (name == null || name.isBlank() || description == null || description.isBlank()) {
                throw new IllegalStateException("직접 등록 정책 이름·설명이 비었습니다: " + externalId);
            }
            PolicyType.valueOf(policyType);
            if (region != null && Region.fromCode(region) == null) {
                throw new IllegalStateException("직접 등록 정책 지역 코드가 아닙니다: " + externalId + " " + region);
            }
            if (benefit == null) {
                throw new IllegalStateException("직접 등록 정책 시뮬레이션 혜택 값이 없습니다: " + externalId);
            }
            benefit.validate(externalId);
        }

        public Policy toPolicy() {
            PolicyText text = PolicyText.builder()
                    .supportContent(supportContent)
                    .eligibilityDetail(eligibilityDetail)
                    .incomeCondition(incomeCondition)
                    .applyMethod(applyMethod)
                    .applyPeriod(applyPeriod)
                    .applyUrl(applyUrl)
                    .referenceUrl(referenceUrl)
                    .contactInfo(contactInfo)
                    .etcNote(etcNote)
                    .build();
            return Policy.create(name, description, minAge, maxAge, null, null, PolicyType.valueOf(policyType), region,
                    externalId, SOURCE, text);
        }
    }

    /** 시뮬레이션 혜택 값. 정책 혜택 추출(#162)과 같은 의미다 */
    public record ManualBenefit(String type, Long monthlyAmount, Integer months, Long oneTimeAmount, boolean maximum,
                                String evidence) {

        void validate(String externalId) {
            BenefitType benefitType = BenefitType.valueOf(type);
            boolean monthly = monthlyAmount != null && monthlyAmount > 0;
            boolean oneTime = oneTimeAmount != null && oneTimeAmount > 0;
            if (benefitType == BenefitType.CASH && !monthly && !oneTime) {
                throw new IllegalStateException("CASH인데 금액이 없습니다: " + externalId);
            }
            if (monthly && (months == null || months < 1)) {
                throw new IllegalStateException("월 금액이 있으면 개월 수가 필요합니다: " + externalId);
            }
        }

        public PolicyBenefit toPolicyBenefit(String promptVersion, String sourceHash, java.time.LocalDateTime extractedAt) {
            return PolicyBenefit.builder()
                    .type(BenefitType.valueOf(type))
                    .monthlyAmount(monthlyAmount)
                    .benefitMonths(monthlyAmount == null ? null : months)
                    .oneTimeAmount(oneTimeAmount)
                    .maximum(maximum)
                    .evidence(evidence)
                    .promptVersion(promptVersion)
                    .sourceHash(sourceHash)
                    .extractedAt(extractedAt)
                    .build();
        }
    }
}
