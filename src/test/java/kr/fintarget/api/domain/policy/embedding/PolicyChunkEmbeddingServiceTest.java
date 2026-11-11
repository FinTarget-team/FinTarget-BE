package kr.fintarget.api.domain.policy.embedding;

import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiClientException.Reason;
import kr.fintarget.api.domain.ai.client.AiEmbeddingClient;
import kr.fintarget.api.domain.policy.embedding.PolicyChunkEmbeddingRepository.ChunkEmbedding;
import kr.fintarget.api.domain.policy.embedding.PolicyChunkEmbeddingRepository.EmbeddingTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyChunkEmbeddingServiceTest {

    private static final String MODEL = "text-embedding-3-small";

    private PolicyChunkEmbeddingRepository repository;
    private AiEmbeddingClient client;
    private PolicyChunkEmbeddingService service;

    @BeforeEach
    void setUp() {
        repository = mock(PolicyChunkEmbeddingRepository.class);
        client = mock(AiEmbeddingClient.class);
        when(client.model()).thenReturn(MODEL);
        // 입력 수만큼 벡터를 돌려준다
        when(client.embed(anyList())).thenAnswer(invocation -> {
            List<?> inputs = invocation.getArgument(0);
            return inputs.stream().map(input -> new float[AiEmbeddingClient.DIMENSIONS]).toList();
        });
        service = service(2);
    }

    private PolicyChunkEmbeddingService service(int batchSize) {
        return new PolicyChunkEmbeddingService(repository, client,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), batchSize);
    }

    private static List<EmbeddingTarget> targets(int count) {
        return IntStream.range(0, count).mapToObj(i -> new EmbeddingTarget(UUID.randomUUID(), "청크 " + i)).toList();
    }

    @Test
    void 대상을_배치_크기로_나눠_호출하고_청크_순서대로_저장한다() {
        List<EmbeddingTarget> targets = targets(5);
        when(repository.findTargets(false, MODEL)).thenReturn(targets);

        PolicyEmbeddingRefreshResult result = service.refreshPending();

        assertThat(result.model()).isEqualTo(MODEL);
        assertThat(result.targetCount()).isEqualTo(5);
        assertThat(result.embeddedCount()).isEqualTo(5);
        assertThat(result.failedCount()).isZero();
        assertThat(result.skippedCount()).isZero();
        verify(client).embed(List.of("청크 0", "청크 1"));
        verify(client).embed(List.of("청크 2", "청크 3"));
        verify(client).embed(List.of("청크 4"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChunkEmbedding>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository, times(3)).upsertAll(saved.capture(), eq(MODEL), any(LocalDateTime.class));
        assertThat(saved.getAllValues().stream().flatMap(List::stream).map(ChunkEmbedding::chunkId).toList())
                .isEqualTo(targets.stream().map(EmbeddingTarget::chunkId).toList());
    }

    @Test
    void all이면_전체_청크를_대상으로_조회한다() {
        when(repository.findTargets(true, MODEL)).thenReturn(targets(1));

        assertThat(service.refreshAll().embeddedCount()).isEqualTo(1);
        verify(repository).findTargets(true, MODEL);
    }

    @Test
    void 대상이_없으면_호출하지_않는다() {
        when(repository.findTargets(false, MODEL)).thenReturn(List.of());

        PolicyEmbeddingRefreshResult result = service.refreshPending();

        assertThat(result.targetCount()).isZero();
        verify(client, never()).embed(anyList());
    }

    @Test
    void 배치_하나가_실패해도_나머지_배치는_계속한다() {
        when(repository.findTargets(false, MODEL)).thenReturn(targets(5));
        when(client.embed(List.of("청크 0", "청크 1"))).thenThrow(AiClientException.of(Reason.SERVER_ERROR));

        PolicyEmbeddingRefreshResult result = service.refreshPending();

        assertThat(result.failedCount()).isEqualTo(2);
        assertThat(result.embeddedCount()).isEqualTo(3);
        verify(repository, times(2)).upsertAll(anyList(), eq(MODEL), any(LocalDateTime.class));
    }

    @Test
    void AI를_쓸_수_없으면_바로_멈추고_남은_청크는_건너뛴다() {
        when(repository.findTargets(false, MODEL)).thenReturn(targets(5));
        when(client.embed(anyList())).thenThrow(AiClientException.of(Reason.QUOTA_EXCEEDED));

        PolicyEmbeddingRefreshResult result = service.refreshPending();

        assertThat(result.skippedCount()).isEqualTo(5);
        assertThat(result.embeddedCount()).isZero();
        verify(client, times(1)).embed(anyList());
        verify(repository, never()).upsertAll(anyList(), any(), any());
    }

    @Test
    void 인증_실패도_바로_멈춘다() {
        when(repository.findTargets(false, MODEL)).thenReturn(targets(3));
        when(client.embed(anyList())).thenThrow(AiClientException.of(Reason.AUTH_FAILED));

        PolicyEmbeddingRefreshResult result = service.refreshPending();

        assertThat(result.skippedCount()).isEqualTo(3);
        verify(client, times(1)).embed(anyList());
    }

    @Test
    void 저장에_실패한_배치는_실패로_세고_다음_배치를_계속한다() {
        when(repository.findTargets(false, MODEL)).thenReturn(targets(3));
        doThrow(new DataIntegrityViolationException("fk"))
                .doNothing()
                .when(repository).upsertAll(anyList(), eq(MODEL), any(LocalDateTime.class));

        PolicyEmbeddingRefreshResult result = service.refreshPending();

        assertThat(result.failedCount()).isEqualTo(2);
        assertThat(result.embeddedCount()).isEqualTo(1);
    }

    @Test
    void 배치_크기는_1_이상_2048_이하만_허용한다() {
        assertThatThrownBy(() -> service(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service(2049)).isInstanceOf(IllegalArgumentException.class);
        service(2048);
    }
}
