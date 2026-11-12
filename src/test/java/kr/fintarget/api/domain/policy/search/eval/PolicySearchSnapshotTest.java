package kr.fintarget.api.domain.policy.search.eval;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchSnapshot.SnapshotChunk;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchSnapshot.SnapshotPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 스냅샷 로더 (#184). 표본(src/test/resources/ai-eval/search-snapshot-sample)은 docs/sql/184_export.sql과 같은
 * COPY 텍스트 형식(역슬래시 두 번)이며, 테스트를 위해 임베딩 차원을 3으로 줄였다.
 */
class PolicySearchSnapshotTest {

    static final Path SAMPLE = Path.of("src/test/resources/ai-eval/search-snapshot-sample");

    @Test
    void 표본을_읽어_건수와_원문과_임베딩을_복원한다() {
        PolicySearchSnapshot snapshot = PolicySearchSnapshot.load(SAMPLE);

        assertThat(snapshot.manifest().embeddingModels()).containsExactly("text-embedding-3-small");
        assertThat(snapshot.policies()).hasSize(3);
        assertThat(snapshot.chunks()).hasSize(5);
        assertThat(snapshot.dimensions()).isEqualTo(3);
        assertThat(snapshot.chunksWithoutEmbedding()).isEqualTo(1);

        SnapshotChunk first = snapshot.chunks().getFirst();
        assertThat(first.content()).isEqualTo("정책명: 청년 월세 지원\n[지원 내용]\n월 20만원");
        assertThat(first.vector()).containsExactly(1f, 0f, 0f);

        SnapshotPolicy kinfa = snapshot.policies().get(1);
        assertThat(kinfa.key()).isEqualTo(new PolicyKey("KINFA", "14|청년 전세 대출"));
        // 원문의 역슬래시는 COPY 이스케이프를 푼 뒤 하나로 남는다
        assertThat(kinfa.supportContent()).isEqualTo("대출 한도: 2억원\\경로 표기");
        assertThat(kinfa.region()).isNull();
        assertThat(kinfa.minAge()).isNull();
    }

    @Test
    void benefitType을_내보낸_스냅샷은_대출_상품으로_복원한다(@TempDir Path dir) throws IOException {
        for (String file : List.of("manifest.jsonl", "chunk.jsonl", "embedding.jsonl")) {
            Files.copy(SAMPLE.resolve(file), dir.resolve(file));
        }
        String policies = Files.readString(SAMPLE.resolve("policy.jsonl"), StandardCharsets.UTF_8)
                .replace("\"etcNote\":null}", "\"etcNote\":null,\"benefitType\":\"LOAN\"}");
        Files.writeString(dir.resolve("policy.jsonl"), policies, StandardCharsets.UTF_8);

        assertThat(PolicySearchSnapshot.load(dir).policies())
                .allSatisfy(policy -> assertThat(policy.toPolicy().isLoanProduct()).isTrue());
    }

    @Test
    void benefitType이_없는_이전_스냅샷도_읽히고_대출_상품은_없다() {
        assertThat(PolicySearchSnapshot.load(SAMPLE).policies())
                .allSatisfy(policy -> assertThat(policy.toPolicy().isLoanProduct()).isFalse());
    }

    @Test
    void 스냅샷_정책을_저장_전_Policy로_바꾼다() {
        Policy policy = PolicySearchSnapshot.load(SAMPLE).policies().getFirst().toPolicy();

        assertThat(policy.getPolicyId()).isNull();
        assertThat(policy.getName()).isEqualTo("청년 월세 지원");
        assertThat(policy.getSource()).isEqualTo("YOUTH_CENTER");
        assertThat(policy.getExternalId()).isEqualTo("R-1");
        assertThat(policy.regionCodes()).containsExactly("SEOUL");
        assertThat(policy.getText().getSupportContent()).isEqualTo("월 20만원\n최대 12개월");
    }

    @Test
    void COPY_텍스트_이스케이프를_푼다() {
        assertThat(PolicySearchSnapshot.decodeCopyText("a\\\\nb")).isEqualTo("a\\nb");
        assertThat(PolicySearchSnapshot.decodeCopyText("a\\nb\\tc\\rd")).isEqualTo("a\nb\tc\rd");
        assertThat(PolicySearchSnapshot.decodeCopyText("{\"k\":\"그대로\"}")).isEqualTo("{\"k\":\"그대로\"}");
        assertThat(PolicySearchSnapshot.decodeCopyText("끝\\")).isEqualTo("끝\\");
    }

    @Test
    void pgvector_텍스트_형식을_읽는다() {
        assertThat(PolicySearchSnapshot.parseVector("[0.5,-1.25e-3, 2]")).containsExactly(0.5f, -0.00125f, 2f);
        assertThatThrownBy(() -> PolicySearchSnapshot.parseVector("0.5,1")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PolicySearchSnapshot.parseVector("[]")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 건수가_manifest와_다르면_거부한다(@TempDir Path dir) throws IOException {
        copySample(dir);
        Files.writeString(dir.resolve("manifest.jsonl"), Files.readString(dir.resolve("manifest.jsonl"))
                .replace("\"policyCount\":3", "\"policyCount\":4"), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> PolicySearchSnapshot.load(dir))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("정책 수");
    }

    @Test
    void 임베딩_차원이_섞여_있으면_거부한다(@TempDir Path dir) throws IOException {
        copySample(dir);
        Files.writeString(dir.resolve("embedding.jsonl"), Files.readString(dir.resolve("embedding.jsonl"))
                .replace("[0,1,0]", "[0,1]"), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> PolicySearchSnapshot.load(dir))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("차원");
    }

    @Test
    void 없는_청크의_임베딩이나_없는_정책의_청크는_거부한다(@TempDir Path dir) throws IOException {
        copySample(dir);
        String unknown = UUID.randomUUID().toString();
        Files.writeString(dir.resolve("embedding.jsonl"), Files.readString(dir.resolve("embedding.jsonl"))
                .replace("00000000-0000-0000-0000-000000000004", unknown), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> PolicySearchSnapshot.load(dir))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("없는 청크의 임베딩");

        copySample(dir);
        Files.writeString(dir.resolve("chunk.jsonl"), Files.readString(dir.resolve("chunk.jsonl"))
                .replace("\"policyId\":\"10000000-0000-0000-0000-000000000003\"", "\"policyId\":\"" + unknown + "\""),
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> PolicySearchSnapshot.load(dir))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("없는 정책의 청크");
    }

    @Test
    void 정책_키는_첫_콜론에서_나눈다() {
        PolicyKey key = PolicyKey.parse("KINFA:14|징검다리:2차");

        assertThat(key).isEqualTo(new PolicyKey("KINFA", "14|징검다리:2차"));
        assertThat(key.toString()).isEqualTo("KINFA:14|징검다리:2차");
        assertThatThrownBy(() -> PolicyKey.parse("KINFA")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PolicyKey.parse(":R-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PolicyKey.parse("KINFA:")).isInstanceOf(IllegalArgumentException.class);
    }

    private static void copySample(Path dir) throws IOException {
        for (String name : List.of("manifest.jsonl", "policy.jsonl", "chunk.jsonl", "embedding.jsonl")) {
            Files.copy(SAMPLE.resolve(name), dir.resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
