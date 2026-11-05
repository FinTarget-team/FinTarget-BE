package kr.fintarget.api.domain.policy.search;

import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiEmbeddingClient;
import kr.fintarget.api.domain.ai.client.AiUnavailableException;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.search.PolicyChunkSearchRepository.ChunkMatch;
import kr.fintarget.api.domain.policy.service.PolicyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 정책 검색의 묶기·유사도 하한·k 로직 (#182). 이 부분은 SQL이 아니라 Java에 있어 H2 없이 검증된다
 * (#184가 k·하한값을 조정하므로 테스트로 덮어 둔다).
 */
@ExtendWith(MockitoExtension.class)
class PolicySearchServiceTest {

    private static final String USER_ID = "user-1";
    private static final float[] VECTOR = {0.1f, 0.2f};

    @Mock
    private PolicyService policyService;

    @Mock
    private PolicyChunkSearchRepository searchRepository;

    @Mock
    private AiEmbeddingClient embeddingClient;

    private PolicySearchService service(int k, double maxDistance, int chunkMultiplier) {
        return new PolicySearchService(policyService, searchRepository, embeddingClient, k, maxDistance, chunkMultiplier);
    }

    private Policy policy(String name) {
        Policy policy = Policy.create(name, "설명", null, null, null, null, PolicyType.ETC, null, null, null);
        ReflectionTestUtils.setField(policy, "policyId", UUID.randomUUID());
        return policy;
    }

    private ChunkMatch match(Policy policy, String label, String content, double distance) {
        return new ChunkMatch(policy.getPolicyId(), UUID.randomUUID(), label, content, distance);
    }

    private void stubEmbedding() {
        when(embeddingClient.embed(List.of("질문"))).thenReturn(List.of(VECTOR));
    }

    @Test
    void 나이나_소득이_없는_사용자는_빈_결과이고_임베딩을_호출하지_않는다() {
        when(policyService.findCandidatePolicies(USER_ID, null)).thenReturn(List.of());

        assertThat(service(5, 0.5, 3).search(USER_ID, "질문")).isEmpty();

        verify(embeddingClient, never()).embed(anyList());
        verify(searchRepository, never()).search(anyList(), any(), anyInt());
    }

    @Test
    void 같은_정책의_청크가_여러_개면_가장_가까운_것_하나로_묶인다() {
        Policy a = policy("정책 A");
        Policy b = policy("정책 B");
        when(policyService.findCandidatePolicies(USER_ID, null)).thenReturn(List.of(a, b));
        stubEmbedding();
        when(searchRepository.search(anyList(), eq(VECTOR), anyInt())).thenReturn(List.of(
                match(a, "지원 내용", "가장 가까운 청크", 0.10),
                match(a, "자격 조건", "같은 정책의 다른 청크", 0.20),
                match(b, "전체", "다른 정책", 0.30)
        ));

        List<PolicySearchHit> hits = service(5, 0.5, 3).search(USER_ID, "질문");

        assertThat(hits).extracting(hit -> hit.policy().getName()).containsExactly("정책 A", "정책 B");
        assertThat(hits.getFirst().sectionLabel()).isEqualTo("지원 내용");
        assertThat(hits.getFirst().content()).isEqualTo("가장 가까운 청크");
        assertThat(hits.getFirst().distance()).isEqualTo(0.10);
    }

    @Test
    void 유사도_하한값을_넘는_결과는_제외된다() {
        Policy near = policy("가까운 정책");
        Policy far = policy("먼 정책");
        when(policyService.findCandidatePolicies(USER_ID, null)).thenReturn(List.of(near, far));
        stubEmbedding();
        when(searchRepository.search(anyList(), eq(VECTOR), anyInt())).thenReturn(List.of(
                match(near, "전체", "본문", 0.30),
                match(far, "전체", "본문", 0.51)
        ));

        List<PolicySearchHit> hits = service(5, 0.5, 3).search(USER_ID, "질문");

        assertThat(hits).extracting(hit -> hit.policy().getName()).containsExactly("가까운 정책");
    }

    @Test
    void 하한값을_넘는_결과만_있으면_빈_결과다() {
        Policy far = policy("먼 정책");
        when(policyService.findCandidatePolicies(USER_ID, null)).thenReturn(List.of(far));
        stubEmbedding();
        when(searchRepository.search(anyList(), eq(VECTOR), anyInt()))
                .thenReturn(List.of(match(far, "전체", "본문", 0.9)));

        assertThat(service(5, 0.5, 3).search(USER_ID, "질문")).isEmpty();
    }

    @Test
    void 묶은_결과는_k개를_넘지_않고_가까운_순서다() {
        List<Policy> policies = List.of(policy("1"), policy("2"), policy("3"), policy("4"));
        when(policyService.findCandidatePolicies(USER_ID, null)).thenReturn(policies);
        stubEmbedding();
        when(searchRepository.search(anyList(), eq(VECTOR), anyInt())).thenReturn(List.of(
                match(policies.get(0), "전체", "본문", 0.10),
                match(policies.get(1), "전체", "본문", 0.20),
                match(policies.get(2), "전체", "본문", 0.30),
                match(policies.get(3), "전체", "본문", 0.40)
        ));

        List<PolicySearchHit> hits = service(2, 0.5, 3).search(USER_ID, "질문");

        assertThat(hits).extracting(hit -> hit.policy().getName()).containsExactly("1", "2");
    }

    // 조회 상한은 k × chunk-multiplier 여야 한다. 한 정책이 청크 여러 개를 차지해도 k개를 채우려면 넉넉히 가져와야 한다
    @Test
    void 청크_조회_상한은_k와_배수의_곱이다() {
        Policy a = policy("정책 A");
        when(policyService.findCandidatePolicies(USER_ID, null)).thenReturn(List.of(a));
        stubEmbedding();
        when(searchRepository.search(anyList(), eq(VECTOR), eq(15))).thenReturn(List.of());

        service(5, 0.5, 3).search(USER_ID, "질문");

        verify(searchRepository).search(anyList(), eq(VECTOR), eq(15));
    }

    @Test
    void 임베딩_호출이_실패하면_예외가_그대로_전파된다() {
        when(policyService.findCandidatePolicies(USER_ID, null)).thenReturn(List.of(policy("정책 A")));
        when(embeddingClient.embed(List.of("질문")))
                .thenThrow(new AiUnavailableException(AiClientException.Reason.DISABLED));

        assertThatThrownBy(() -> service(5, 0.5, 3).search(USER_ID, "질문"))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void k와_배수_설정값이_1보다_작으면_기동에_실패한다() {
        assertThatThrownBy(() -> service(0, 0.5, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service(5, 0.5, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
