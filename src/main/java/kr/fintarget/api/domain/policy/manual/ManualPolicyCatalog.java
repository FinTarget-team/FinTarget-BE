package kr.fintarget.api.domain.policy.manual;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.fintarget.api.domain.policy.benefit.BenefitType;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyBenefit;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.user.code.Region;
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
 */
@Component
public class ManualPolicyCatalog {

    public static final String SOURCE = "MANUAL";
    static final String RESOURCE = "policy/manual-policies.json";

    private final Map<String, ManualPolicy> byExternalId;
    private final List<ManualPolicy> ordered;

    public ManualPolicyCatalog() {
        this(load(RESOURCE));
    }

    ManualPolicyCatalog(List<ManualPolicy> policies) {
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
    }

    public List<ManualPolicy> policies() {
        return ordered;
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
     * 다른 출처 정책이면 extracted를 그대로 돌려준다.
     */
    public PolicyBenefit applyBenefit(Policy policy, PolicyBenefit extracted) {
        return find(policy)
                .map(manual -> manual.benefit().toPolicyBenefit(
                        extracted == null ? null : extracted.getPromptVersion(),
                        extracted == null ? null : extracted.getSourceHash(),
                        extracted == null ? null : extracted.getExtractedAt()))
                .orElse(extracted);
    }

    private static List<ManualPolicy> load(String path) {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return mapper.readValue(in, Document.class).policies();
        } catch (IOException e) {
            throw new UncheckedIOException("직접 등록 정책 파일을 읽을 수 없습니다: " + path, e);
        }
    }

    record Document(String description, List<ManualPolicy> policies) {
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
