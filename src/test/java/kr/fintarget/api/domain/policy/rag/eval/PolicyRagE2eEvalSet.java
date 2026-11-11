package kr.fintarget.api.domain.policy.rag.eval;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.fintarget.api.domain.policy.rag.PolicyRagStatus;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalSet.Category;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalSet.RagCase;
import kr.fintarget.api.domain.policy.search.eval.PolicyKey;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 통합 정책 AI 답변 평가셋 2단계 b (#184, 검색부터 답변까지). src/test/resources/ai-eval/policy-rag-e2e-cases.json을 읽는다.
 *
 * <p>문항 형식은 2단계 a({@link RagCase})와 같고 다음만 다르다.
 * <ul>
 *   <li>hits는 비운다. 스냅샷 검색 결과가 근거 후보가 된다</li>
 *   <li>profile은 검색 평가셋(policy-search-cases.json)의 profiles 키다 (나이·소득·지역 → 후보 정책 필터)</li>
 *   <li>expectedSources·forbiddenSources는 정책 키("출처:외부ID")다. 스냅샷에 있어야 한다</li>
 *   <li>PROFILE_INCOMPLETE 상태를 기대할 수 있다 (나이·소득이 없는 프로필)</li>
 * </ul>
 */
public record PolicyRagE2eEvalSet(String description, Map<String, String> guide, List<RagCase> cases) {

    public static final String RESOURCE = "ai-eval/policy-rag-e2e-cases.json";

    public PolicyRagE2eEvalSet {
        cases = List.copyOf(cases);
    }

    public static PolicyRagE2eEvalSet load() {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return mapper.readValue(in, PolicyRagE2eEvalSet.class);
        } catch (IOException e) {
            throw new UncheckedIOException("평가셋을 읽을 수 없습니다: " + RESOURCE, e);
        }
    }

    /**
     * 검색 평가셋 프로필과 맞는지 확인한다. 맞지 않으면 IllegalStateException.
     */
    public PolicyRagE2eEvalSet validate(PolicySearchEvalSet searchEvalSet) {
        Set<String> ids = new HashSet<>();
        for (RagCase c : cases) {
            check(ids.add(c.id()), "문항 ID 중복: " + c.id());
            check(searchEvalSet.profiles().containsKey(c.profile()), "검색 평가셋에 없는 프로필: " + c.id() + " → " + c.profile());
            check(c.hits().isEmpty(), "2단계 b는 hits를 비운다 (검색 결과를 쓴다): " + c.id());
            check(!c.expectedStatus().isEmpty(), "기대 상태가 비어 있습니다: " + c.id());
            for (String key : sourceKeys(c).toList()) {
                try {
                    PolicyKey.parse(key);
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException("평가셋 검증 실패: 정책 키 형식이 아닙니다: " + c.id() + " → " + key, e);
                }
            }
            check(c.expectedSources().stream().noneMatch(c.forbiddenSources()::contains), "기대·금지 근거가 겹칩니다: " + c.id());
            check(c.category() != Category.NO_RELEVANT
                    || (c.expectedStatus().equals(List.of(PolicyRagStatus.NO_EVIDENCE)) && c.expectedSources().isEmpty()),
                    "NO_RELEVANT 문항은 NO_EVIDENCE만 기대하고 기대 근거가 없어야 합니다: " + c.id());
            check(c.category() != Category.PROFILE_INCOMPLETE
                    || c.expectedStatus().equals(List.of(PolicyRagStatus.PROFILE_INCOMPLETE)),
                    "PROFILE_INCOMPLETE 문항은 PROFILE_INCOMPLETE만 기대합니다: " + c.id());
            check(c.expectedStatus().contains(PolicyRagStatus.ANSWERED) || c.expectedSources().isEmpty(),
                    "ANSWERED를 허용하지 않는데 기대 근거가 있습니다: " + c.id());
        }
        return this;
    }

    /** 문항들이 가리키는 정책 키 전체 (스냅샷에 있는지 확인용) */
    public List<String> sourceKeys() {
        return cases.stream().flatMap(PolicyRagE2eEvalSet::sourceKeys).distinct().toList();
    }

    private static Stream<String> sourceKeys(RagCase c) {
        return Stream.concat(c.expectedSources().stream(), c.forbiddenSources().stream());
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("평가셋 검증 실패: " + message);
        }
    }
}
