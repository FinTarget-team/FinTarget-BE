package kr.fintarget.api.domain.policy.chunk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 전체 정책 검색(통합 RAG)용 정책 원문 청크 (#180). 임베딩(vector) 컬럼은 #181에서 추가한다.
 *
 * <p>policy_id는 FK 없이 값만 저장한다. 정책 삭제 시 PolicySyncWriter가 청크를 함께 지운다 (CASCADE에 의존하지 않음).
 * 한 정책의 청크는 모두 같은 source_hash를 갖고, 원문이 바뀌면 정책 단위로 지우고 다시 만든다 (PolicyChunkService).
 */
@Entity
@Table(name = "policy_chunk", indexes = @Index(name = "idx_policy_chunk_policy_id", columnList = "policy_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "chunk_id", columnDefinition = "uuid")
    private UUID chunkId;

    @Column(name = "policy_id", columnDefinition = "uuid", nullable = false)
    private UUID policyId;

    // 청크에 담긴 섹션 라벨. 정책 전체가 청크 1개면 "전체", 나뉜 청크는 담긴 섹션 라벨을 쉼표로 이은 값
    @Column(name = "section_label", nullable = false, length = 300)
    private String sectionLabel;

    // 정책 안에서의 청크 순서 (0부터)
    @Column(name = "seq", nullable = false)
    private int seq;

    // 머리말(정책명, 지역, 나이)과 [라벨] 섹션 본문
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 이 청크를 만든 원문·분할 규칙의 SHA-256 (PolicyChunker.chunk 참고)
    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    private PolicyChunk(UUID policyId, String sectionLabel, int seq, String content, String sourceHash) {
        this.policyId = policyId;
        this.sectionLabel = sectionLabel;
        this.seq = seq;
        this.content = content;
        this.sourceHash = sourceHash;
    }

    public static PolicyChunk of(UUID policyId, PolicyChunks.Draft draft, String sourceHash) {
        return new PolicyChunk(policyId, draft.sectionLabel(), draft.seq(), draft.content(), sourceHash);
    }
}
