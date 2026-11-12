package kr.fintarget.api.domain.policy.search.eval;

import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.CaseType;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.Profile;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.SearchCase;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchPool.CasePool;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchPool.Item;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchSnapshot.Manifest;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchSnapshot.SnapshotChunk;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchSnapshot.SnapshotPolicy;
import kr.fintarget.api.domain.policy.service.PolicyService;
import kr.fintarget.api.domain.user.validation.BirthYearValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 라벨링 풀의 벡터 상위 건수 (#184). {@link SearchConfig#WIDE}의 k=20에 묶이지 않고 요청한 건수만큼 담는지 확인한다.
 *
 * <p>서울 정책 60건, 부산 정책 30건이고 정책마다 청크가 6개다. 한 정책의 청크가 연달아 가깝게 놓이므로
 * 청크를 k×5개만 조회하면 50건을 채우지 못한다.
 */
@DataJpaTest
@Import(PolicyService.class)
class PolicySearchPoolVectorTopTest {

    private static final int CHUNKS_PER_POLICY = 6;
    private static final String SEOUL_QUESTION = "서울 정책 알려줘";
    private static final String BUSAN_QUESTION = "부산 정책 알려줘";

    private static final PolicySearchEvalSet EVAL_SET = new PolicySearchEvalSet("테스트", Map.of(),
            Map.of("seoul-25", new Profile("SEOUL", 25, 2_500_000L, null),
                    "busan-25", new Profile("BUSAN", 25, 2_500_000L, null)),
            List.of(new SearchCase("region-seoul", CaseType.REGION, "seoul-25", SEOUL_QUESTION, List.of(), null, false, null),
                    new SearchCase("region-busan", CaseType.REGION, "busan-25", BUSAN_QUESTION, List.of(), null, false, null)));

    private static final QuestionEmbeddingCache CACHE = QuestionEmbeddingCache.inMemory("text-embedding-3-small", Map.of(
            SEOUL_QUESTION, new float[]{1f, 0f},
            BUSAN_QUESTION, new float[]{1f, 0f}));

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PolicyService policyService;

    private PolicySearchPool pool;

    @BeforeEach
    void setUp() {
        PolicySearchSnapshot snapshot = snapshot();
        PolicySearchSnapshotLoader.Loaded loaded = PolicySearchSnapshotLoader.load(snapshot, policyRepository);
        PolicySearchEvalRunner runner = new PolicySearchEvalRunner(EVAL_SET, loaded, policyService, CACHE.client(),
                Year.now(BirthYearValidator.ZONE));
        pool = new PolicySearchPool(runner, snapshot);
    }

    @Test
    void 후보가_요청_건수보다_많으면_요청한_건수만큼_벡터_후보를_담는다() {
        CasePool seoul = poolOf("region-seoul");

        assertThat(seoul.candidateCount()).isEqualTo(60);
        assertThat(seoul.items()).extracting(Item::vectorRank)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 50).boxed().toList());
        // 거리순이므로 앞쪽 50개 정책이다
        assertThat(seoul.items().getLast().key()).isEqualTo(new PolicyKey("YOUTH_CENTER", "SEOUL-49"));
    }

    @Test
    void 조건을_통과한_정책이_요청_건수보다_적으면_그_수만큼_담는다() {
        CasePool busan = poolOf("region-busan");

        assertThat(busan.candidateCount()).isEqualTo(30);
        assertThat(busan.items()).hasSize(30).allSatisfy(item -> assertThat(item.vectorRank()).isNotNull());
    }

    private CasePool poolOf(String id) {
        return pool.build(50, 0).stream().filter(c -> c.evalCase().id().equals(id)).findFirst().orElseThrow();
    }

    private static PolicySearchSnapshot snapshot() {
        List<SnapshotPolicy> policies = new ArrayList<>();
        List<SnapshotChunk> chunks = new ArrayList<>();
        addPolicies("SEOUL", 60, policies, chunks);
        addPolicies("BUSAN", 30, policies, chunks);
        Manifest manifest = new Manifest("2026-11-06T00:00:00+09:00", policies.size(), chunks.size(), chunks.size(),
                List.of("text-embedding-3-small"));
        return new PolicySearchSnapshot(manifest, policies, chunks);
    }

    private static void addPolicies(String region, int count, List<SnapshotPolicy> policies, List<SnapshotChunk> chunks) {
        for (int i = 0; i < count; i++) {
            String externalId = region + "-" + i;
            UUID policyId = uuid("policy-" + externalId);
            policies.add(new SnapshotPolicy(policyId, "YOUTH_CENTER", externalId, "정책 " + externalId, "설명", "HOUSING",
                    region, 19, 34, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null));
            for (int seq = 0; seq < CHUNKS_PER_POLICY; seq++) {
                // 번호가 뒤일수록 질문([1,0])에서 멀어진다
                double angle = (i * CHUNKS_PER_POLICY + seq) * 0.002;
                chunks.add(new SnapshotChunk(uuid("chunk-" + externalId + "-" + seq), policyId, "전체", seq,
                        "정책명: 정책 " + externalId + "\n본문", new float[]{(float) Math.cos(angle), (float) Math.sin(angle)}));
            }
        }
    }

    private static UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }
}
