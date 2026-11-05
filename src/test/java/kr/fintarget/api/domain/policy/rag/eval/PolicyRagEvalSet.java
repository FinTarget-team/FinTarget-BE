package kr.fintarget.api.domain.policy.rag.eval;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.fintarget.api.domain.ai.chat.eval.PolicyQaEvalSet;
import kr.fintarget.api.domain.ai.chat.eval.PolicyQaEvalSet.PolicySnapshot;
import kr.fintarget.api.domain.ai.chat.service.UserProfileSummary;
import kr.fintarget.api.domain.policy.chunk.PolicyChunker;
import kr.fintarget.api.domain.policy.chunk.PolicyChunks;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.rag.PolicyRagStatus;
import kr.fintarget.api.domain.policy.search.PolicySearchHit;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 통합 정책 AI 답변 평가셋 2단계 a (#184, 고정 청크). src/test/resources/ai-eval/policy-rag-cases.json을 읽는다.
 *
 * <p>정책 원문과 사용자 정보는 {@link PolicyQaEvalSet}(#126)의 스냅샷을 재사용한다. 원문은 운영과 같은
 * {@link PolicyChunker} 기본 설정(800자/100자)으로 청크화해 검색 결과({@link PolicySearchHit})로 만든다.
 * 정책이 청크 여러 개로 나뉘면 첫 번째 청크를 쓴다 (현재 16건은 모두 청크 1개).
 *
 * <p>정책 ID는 키에서 만든 고정 UUID라, 답변의 근거 정책을 키로 되돌릴 수 있다 ({@link #keyOf}).
 */
public record PolicyRagEvalSet(String description, Map<String, String> guide,
                               Map<String, SyntheticPolicy> syntheticPolicies, List<RagCase> cases) {

    public static final String RESOURCE = "ai-eval/policy-rag-cases.json";
    private static final PolicyChunker CHUNKER = new PolicyChunker(800, 100);

    public PolicyRagEvalSet {
        syntheticPolicies = syntheticPolicies == null ? Map.of() : Map.copyOf(syntheticPolicies);
        cases = List.copyOf(cases);
    }

    public static PolicyRagEvalSet load() {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return mapper.readValue(in, PolicyRagEvalSet.class);
        } catch (IOException e) {
            throw new UncheckedIOException("평가셋을 읽을 수 없습니다: " + RESOURCE, e);
        }
    }

    /**
     * 평가셋 정책과 사용자 정보를 #126 스냅샷과 묶는다. 키·프로필·근거 범위가 맞지 않으면 IllegalStateException.
     */
    public Bound bind(PolicyQaEvalSet qaEvalSet) {
        Map<String, Policy> policies = new LinkedHashMap<>();
        qaEvalSet.policies().forEach((key, snapshot) -> policies.put(key, withId(key, snapshot.toPolicy())));
        syntheticPolicies.forEach((key, synthetic) -> {
            PolicySnapshot base = qaEvalSet.policies().get(synthetic.base());
            if (base == null) {
                throw new IllegalStateException("합성 정책의 base가 없습니다: " + key + " → " + synthetic.base());
            }
            if (policies.containsKey(key)) {
                throw new IllegalStateException("합성 정책 키가 기존 정책과 겹칩니다: " + key);
            }
            policies.put(key, withId(key, synthetic.apply(base).toPolicy()));
        });

        Set<String> ids = new HashSet<>();
        for (RagCase c : cases) {
            check(ids.add(c.id()), "문항 ID 중복: " + c.id());
            check(qaEvalSet.profiles().containsKey(c.profile()), "없는 프로필: " + c.id() + " → " + c.profile());
            check(!c.hits().isEmpty(), "검색 결과가 비어 있습니다 (2단계 a는 AI를 호출하는 문항만): " + c.id());
            check(new HashSet<>(c.hits()).size() == c.hits().size(), "검색 결과 중복: " + c.id());
            for (String hit : c.hits()) {
                check(policies.containsKey(hit), "없는 정책: " + c.id() + " → " + hit);
            }
            check(c.hits().containsAll(c.expectedSources()), "기대 근거가 검색 결과 밖: " + c.id());
            check(c.hits().containsAll(c.forbiddenSources()), "금지 근거가 검색 결과 밖: " + c.id());
            check(c.expectedSources().stream().noneMatch(c.forbiddenSources()::contains), "기대·금지 근거가 겹칩니다: " + c.id());
            check(!c.expectedStatus().isEmpty(), "기대 상태가 비어 있습니다: " + c.id());
            check(c.category() != Category.NO_RELEVANT
                    || (c.expectedStatus().equals(List.of(PolicyRagStatus.NO_EVIDENCE)) && c.expectedSources().isEmpty()),
                    "NO_RELEVANT 문항은 NO_EVIDENCE만 기대하고 기대 근거가 없어야 합니다: " + c.id());
            check(c.expectedStatus().contains(PolicyRagStatus.ANSWERED) || c.expectedSources().isEmpty(),
                    "ANSWERED를 허용하지 않는데 기대 근거가 있습니다: " + c.id());
            check(!c.expectedStatus().contains(PolicyRagStatus.PROFILE_INCOMPLETE),
                    "PROFILE_INCOMPLETE는 답변 단계에서 나오지 않습니다 (2단계 b 대상): " + c.id());
        }
        return new Bound(this, qaEvalSet, policies);
    }

    private static Policy withId(String key, Policy policy) {
        ReflectionTestUtils.setField(policy, "policyId", UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)));
        return policy;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("평가셋 검증 실패: " + message);
        }
    }

    /**
     * PROFILE_INCOMPLETE(나이·소득 없음)는 서비스 단계에서만 나오므로 2단계 b({@link PolicyRagE2eEvalSet})에서만 쓴다.
     */
    public enum Category {
        SINGLE_MATCH, MULTI_MATCH, NO_RELEVANT, NOT_IN_SOURCE, PROFILE_CHECK, INJECTION, SAME_NAME_REGION, PROFILE_INCOMPLETE
    }

    /**
     * 필드 설명은 파일의 guide 참고.
     */
    public record RagCase(String id, Category category, String profile, String question, List<String> hits,
                          List<PolicyRagStatus> expectedStatus, List<String> expectedSources,
                          List<String> forbiddenSources, List<List<String>> expectedFacts, List<String> mustNotInclude,
                          boolean notInSource, boolean needsCheck, boolean noDefinitiveEligibility, String note) {

        public RagCase {
            hits = hits == null ? List.of() : List.copyOf(hits);
            expectedStatus = expectedStatus == null ? List.of() : List.copyOf(expectedStatus);
            expectedSources = expectedSources == null ? List.of() : List.copyOf(expectedSources);
            forbiddenSources = forbiddenSources == null ? List.of() : List.copyOf(forbiddenSources);
            expectedFacts = expectedFacts == null ? List.of() : expectedFacts.stream().map(List::copyOf).toList();
            mustNotInclude = mustNotInclude == null ? List.of() : List.copyOf(mustNotInclude);
        }
    }

    /**
     * 같은 이름의 지역별 정책 확인용 합성 정책. base 정책 원문에서 지정한 값만 바꾼다 (운영 데이터가 아니다).
     */
    public record SyntheticPolicy(String base, String externalId, String region, String supportContent, String note) {

        PolicySnapshot apply(PolicySnapshot b) {
            return new PolicySnapshot(b.source(), b.policyType(), b.name(),
                    externalId != null ? externalId : b.externalId(),
                    region != null ? region : b.region(),
                    b.minAge(), b.maxAge(), b.incomeLimit(), b.benefitAmount(), b.description(),
                    supportContent != null ? supportContent : b.supportContent(),
                    b.eligibilityDetail(), b.exclusionCriteria(), b.incomeCondition(), b.ageCondition(), b.applyMethod(),
                    b.screeningMethod(), b.requiredDocuments(), b.applyPeriod(), b.businessPeriod(), b.referenceUrl(),
                    b.applyUrl(), b.contactInfo(), b.etcNote());
        }
    }

    /**
     * #126 스냅샷과 묶인 평가셋. 문항별 검색 결과와 사용자 정보를 만든다.
     *
     * @param policies 정책 키 → 고정 ID를 넣은 정책 (합성 정책 포함)
     */
    public record Bound(PolicyRagEvalSet evalSet, PolicyQaEvalSet qaEvalSet, Map<String, Policy> policies) {

        /** 문항의 고정 검색 결과. 목록 순서가 근거 번호이며, 거리는 순서대로 0.10, 0.15, ...로 둔다 (답변 단계는 거리를 쓰지 않음) */
        public List<PolicySearchHit> hitsOf(RagCase c) {
            List<PolicySearchHit> hits = new ArrayList<>();
            for (int i = 0; i < c.hits().size(); i++) {
                String key = c.hits().get(i);
                Policy policy = policies.get(key);
                PolicyChunks.Draft chunk = CHUNKER.chunk(policy).chunks().getFirst();
                hits.add(new PolicySearchHit(policy, UUID.nameUUIDFromBytes((key + "#" + chunk.seq()).getBytes(StandardCharsets.UTF_8)),
                        chunk.sectionLabel(), chunk.content(), 0.10 + i * 0.05));
            }
            return hits;
        }

        public UserProfileSummary profileOf(RagCase c) {
            return qaEvalSet.profiles().get(c.profile());
        }

        /** 답변의 근거 정책을 평가셋 키로 되돌린다 */
        public String keyOf(Policy policy) {
            return policies.entrySet().stream()
                    .filter(entry -> entry.getValue().getPolicyId().equals(policy.getPolicyId()))
                    .map(Map.Entry::getKey).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("평가셋 정책이 아닙니다: " + policy.getPolicyId()));
        }
    }
}
