package kr.fintarget.api.domain.policy.chunk;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.policy.service.PolicySyncWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 JPA(H2)로 청크 저장·재생성·삭제를 확인한다 (#180). 동기화 갱신(updateFrom)과 삭제는 실제 PolicySyncWriter를 거친다.
 */
@DataJpaTest
@Import({PolicyChunkService.class, PolicyChunker.class, PolicySyncWriter.class})
class PolicyChunkServiceIntegrationTest {

    private static final String SOURCE = "YOUTH_CENTER";

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PolicyChunkRepository policyChunkRepository;

    @Autowired
    private PolicyChunkService service;

    @Autowired
    private PolicySyncWriter policySyncWriter;

    private static Policy policy(String externalId, String supportContent) {
        return Policy.create("정책 " + externalId, "설명 " + externalId, 19, 34, null, null, PolicyType.HOUSING,
                null, externalId, SOURCE, PolicyText.builder().supportContent(supportContent).build());
    }

    private List<PolicyChunk> chunksOf(Policy policy) {
        return policyChunkRepository.findByPolicyIdOrderBySeqAsc(policy.getPolicyId());
    }

    private static List<UUID> ids(List<PolicyChunk> chunks) {
        return chunks.stream().map(PolicyChunk::getChunkId).toList();
    }

    @Test
    void 처음_실행하면_청크가_없는_기존_정책_전체를_만들고_두_번째_실행은_건너뛴다() {
        Policy first = policyRepository.save(policy("A", "월 20만원 지원"));
        Policy second = policyRepository.save(policy("B", "월 30만원 지원"));

        PolicyChunkRefreshResult backfill = service.refreshPending();

        assertThat(backfill.policyCount()).isEqualTo(2);
        assertThat(backfill.regeneratedCount()).isEqualTo(2);
        assertThat(backfill.chunkCount()).isEqualTo(2);
        List<PolicyChunk> chunks = chunksOf(first);
        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.getSectionLabel()).isEqualTo(PolicyChunker.WHOLE_POLICY_LABEL);
            assertThat(chunk.getSeq()).isZero();
            assertThat(chunk.getContent()).startsWith("정책명: 정책 A\n").contains("[지원 내용]\n월 20만원 지원");
            assertThat(chunk.getSourceHash()).hasSize(64);
        });
        assertThat(chunksOf(second)).hasSize(1);

        PolicyChunkRefreshResult again = service.refreshPending();

        assertThat(again.regeneratedCount()).isZero();
        assertThat(again.skippedCount()).isEqualTo(2);
        assertThat(ids(chunksOf(first))).isEqualTo(ids(chunks));
    }

    @Test
    void 동기화로_원문이_바뀐_정책만_다시_만들고_청크가_새_원문과_일치한다() {
        Policy changed = policyRepository.save(policy("A", "월 20만원 지원"));
        Policy unchanged = policyRepository.save(policy("B", "월 30만원 지원"));
        service.refreshPending();
        List<UUID> unchangedIds = ids(chunksOf(unchanged));

        policySyncWriter.apply(SOURCE, List.of(policy("A", "월 25만원으로 지원 확대"), policy("B", "월 30만원 지원")));
        PolicyChunkRefreshResult result = service.refreshPending();

        assertThat(result.regeneratedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(chunksOf(changed)).singleElement().satisfies(chunk ->
                assertThat(chunk.getContent()).contains("월 25만원으로 지원 확대").doesNotContain("월 20만원 지원"));
        assertThat(ids(chunksOf(unchanged))).isEqualTo(unchangedIds);
    }

    @Test
    void 긴_정책은_여러_청크로_순서대로_저장하고_다시_만들_때_이전_청크를_남기지_않는다() {
        Policy policy = policyRepository.save(Policy.create("긴 정책", "가".repeat(500), null, null, null, null,
                PolicyType.HOUSING, null, "L-1", SOURCE,
                PolicyText.builder().supportContent("나".repeat(500)).eligibilityDetail("다".repeat(500)).build()));

        service.refreshPending();
        assertThat(chunksOf(policy)).extracting(PolicyChunk::getSeq).containsExactly(0, 1, 2);
        assertThat(chunksOf(policy)).extracting(PolicyChunk::getSectionLabel)
                .containsExactly("정책 설명", "지원 내용", "자격 조건");

        policySyncWriter.apply(SOURCE, List.of(Policy.create("긴 정책", "짧아진 설명", null, null, null, null,
                PolicyType.HOUSING, null, "L-1", SOURCE, null)));
        service.refreshPending();

        assertThat(chunksOf(policy)).singleElement().satisfies(chunk ->
                assertThat(chunk.getContent()).isEqualTo("정책명: 긴 정책\n신청 가능 지역: 전국\n\n[정책 설명]\n짧아진 설명"));
    }

    @Test
    void all이면_원문이_같아도_전체를_다시_만든다() {
        Policy policy = policyRepository.save(policy("A", "월 20만원 지원"));
        service.refreshPending();
        List<UUID> before = ids(chunksOf(policy));

        PolicyChunkRefreshResult result = service.refreshAll();

        assertThat(result.regeneratedCount()).isEqualTo(1);
        assertThat(ids(chunksOf(policy))).hasSize(1).doesNotContainAnyElementsOf(before);
    }

    @Test
    void 동기화에서_삭제되는_정책의_청크도_함께_지운다() {
        Policy kept = policyRepository.save(policy("A", "월 20만원 지원"));
        Policy ended = policyRepository.save(policy("OLD", "종료된 정책"));
        service.refreshPending();
        assertThat(chunksOf(ended)).hasSize(1);

        policySyncWriter.apply(SOURCE, List.of(policy("A", "월 20만원 지원")));

        assertThat(policyRepository.findById(ended.getPolicyId())).isEmpty();
        assertThat(chunksOf(ended)).isEmpty();
        assertThat(chunksOf(kept)).hasSize(1);
    }

    @Test
    void 정책_테이블에_없는_정책의_청크는_갱신할_때_지운다() {
        policyRepository.save(policy("A", "월 20만원 지원"));
        UUID missingPolicyId = UUID.randomUUID();
        policyChunkRepository.save(PolicyChunk.of(missingPolicyId,
                new PolicyChunks.Draft(0, PolicyChunker.WHOLE_POLICY_LABEL, "정책명: 사라진 정책"), "0".repeat(64)));

        PolicyChunkRefreshResult result = service.refreshPending();

        assertThat(result.orphanDeletedCount()).isEqualTo(1);
        assertThat(policyChunkRepository.findByPolicyIdOrderBySeqAsc(missingPolicyId)).isEmpty();
    }
}
