package kr.fintarget.api.domain.policy.search.eval;

import kr.fintarget.api.domain.ai.client.AiEmbeddingClient;
import kr.fintarget.api.domain.ai.usage.AiUsageLimiter;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.policy.search.PolicySearchHit;
import kr.fintarget.api.domain.policy.search.PolicySearchService;
import kr.fintarget.api.domain.policy.service.PolicyService;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.validation.BirthYearValidator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Year;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 스냅샷을 H2에 적재해 운영과 같은 후보 필터(PolicyService)와 검색 로직(PolicySearchService)으로 검색한다 (#184).
 * 질문 임베딩만 고정 벡터로 바꾼다.
 */
@DataJpaTest
@Import(PolicyService.class)
class PolicySearchSnapshotLoaderTest {

    private static final PolicyKey SEOUL_RENT = new PolicyKey("YOUTH_CENTER", "R-1");
    private static final PolicyKey KINFA_LOAN = new PolicyKey("KINFA", "14|청년 전세 대출");
    private static final PolicyKey BUSAN_MOVING = new PolicyKey("YOUTH_CENTER", "R-2");

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PolicyService policyService;

    private final AiUsageLimiter usageLimiter = Mockito.mock(AiUsageLimiter.class);

    private static AiEmbeddingClient fixedEmbedding(float... vector) {
        return new AiEmbeddingClient() {
            @Override
            public String model() {
                return "fixed";
            }

            @Override
            public List<float[]> embed(List<String> inputs) {
                return inputs.stream().map(input -> vector).toList();
            }
        };
    }

    private static User seoulUser() {
        int birthYear = Year.now(BirthYearValidator.ZONE).getValue() - 26;
        return User.builder().id("eval-seoul").provider(SocialProvider.KAKAO).providerId("eval")
                .birthYear(birthYear).income(2_500_000L).region("SEOUL").build();
    }

    @Test
    void 적재한_정책으로_운영과_같은_조건_필터와_검색을_돌린다() {
        PolicySearchSnapshotLoader.Loaded loaded =
                PolicySearchSnapshotLoader.load(PolicySearchSnapshot.load(PolicySearchSnapshotTest.SAMPLE), policyRepository);

        assertThat(loaded.policies()).containsOnlyKeys(SEOUL_RENT, KINFA_LOAN, BUSAN_MOVING);
        assertThat(loaded.searchRepository().size()).isEqualTo(4);

        // 서울 사용자: 부산 정책은 지역 조건으로 빠진다
        List<Policy> candidates = policyService.findCandidatePolicies(seoulUser(), null);
        assertThat(candidates).extracting(loaded::keyOf).containsExactlyInAnyOrder(SEOUL_RENT, KINFA_LOAN);

        PolicySearchService searchService = new PolicySearchService(policyService, loaded.searchRepository(),
                fixedEmbedding(1f, 0f, 0f), usageLimiter, 5, 1.1, 3, 50);
        List<PolicySearchHit> hits = searchService.searchCandidates(candidates, "월세 지원");

        // 월세 정책은 청크 2개(거리 0, 0.2) 중 가까운 것으로 묶이고, 전세 대출(거리 1.0)이 뒤에 온다
        assertThat(hits).extracting(hit -> loaded.keyOf(hit.policy())).containsExactly(SEOUL_RENT, KINFA_LOAN);
        assertThat(hits.getFirst().sectionLabel()).isEqualTo("전체");
        assertThat(hits.getFirst().distance()).isZero();
        verifyNoInteractions(usageLimiter);
    }

    @Test
    void 유사도_하한값은_운영_검색_로직이_그대로_적용한다() {
        PolicySearchSnapshotLoader.Loaded loaded =
                PolicySearchSnapshotLoader.load(PolicySearchSnapshot.load(PolicySearchSnapshotTest.SAMPLE), policyRepository);
        List<Policy> candidates = policyService.findCandidatePolicies(seoulUser(), null);

        PolicySearchService searchService = new PolicySearchService(policyService, loaded.searchRepository(),
                fixedEmbedding(1f, 0f, 0f), usageLimiter, 5, 0.5, 3, 50);

        assertThat(searchService.searchCandidates(candidates, "월세 지원"))
                .extracting(hit -> loaded.keyOf(hit.policy())).containsExactly(SEOUL_RENT);
    }

    @Test
    void 스냅샷에서_적재하지_않은_정책은_키를_찾을_수_없다() {
        PolicySearchSnapshotLoader.Loaded loaded =
                PolicySearchSnapshotLoader.load(PolicySearchSnapshot.load(PolicySearchSnapshotTest.SAMPLE), policyRepository);
        Policy other = policyRepository.save(Policy.create("다른 정책", "설명", null, null, null, null,
                kr.fintarget.api.domain.policy.entity.PolicyType.ETC, null, "X-1", "YOUTH_CENTER"));

        assertThatThrownBy(() -> loaded.keyOf(other)).isInstanceOf(IllegalArgumentException.class);
    }
}
