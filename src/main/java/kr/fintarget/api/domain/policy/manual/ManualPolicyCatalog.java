package kr.fintarget.api.domain.policy.manual;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.fintarget.api.domain.policy.benefit.BenefitType;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyBenefit;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;
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
import java.util.Optional;
import java.util.Set;

/**
 * 공공 API로 수집되지 않아 직접 등록하는 정책 목록 (#245). 본문은 src/main/resources/policy/manual-policies.json에 둔다.
 *
 * <p>출처(source)를 {@link #SOURCE}로 저장해, 온통청년·KINFA 동기화의 삭제·갱신 대상(같은 출처만)에서 빠진다
 * ({@link kr.fintarget.api.domain.policy.service.PolicySyncWriter}).
 * 시뮬레이션 혜택 값(benefit)은 AI 혜택 추출보다 리소스 값을 우선한다 ({@link #applyBenefit}).
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

    public ManualPolicyCatalog() {
        this(load(RESOURCE));
    }

    private ManualPolicyCatalog(Document document) {
        this(document.policies(), document.benefitOverrides() == null ? List.of() : document.benefitOverrides());
    }

    ManualPolicyCatalog(List<ManualPolicy> policies) {
        this(policies, List.of());
    }

    ManualPolicyCatalog(List<ManualPolicy> policies, List<BenefitOverride> benefitOverrides) {
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

    record Document(String description, List<ManualPolicy> policies, List<BenefitOverride> benefitOverrides) {
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
