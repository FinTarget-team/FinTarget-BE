package kr.fintarget.api.domain.policy.search.eval;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 운영 DB에서 내보낸 정책 검색 평가용 스냅샷 (#184). 형식과 내보내기 방법은 docs/sql/184_export.sql 참고.
 *
 * <p>파일 4개(manifest.jsonl, policy.jsonl, chunk.jsonl, embedding.jsonl)는 한 줄에 JSON 객체 1개이며,
 * \copy가 COPY 텍스트 형식으로 써서 역슬래시 등이 이스케이프되어 있다. {@link #decodeCopyText}로 풀어 읽는다.
 *
 * <p>스냅샷은 커밋하지 않는다 (ai-eval-snapshot/은 .gitignore 대상). 경로는 환경 변수 {@value #ENV_DIR}로 받는다.
 *
 * @param chunks 청크와 임베딩. 임베딩이 없는 청크는 vector가 null이며, 운영 검색(JOIN)처럼 검색 대상에서 빠진다
 */
public record PolicySearchSnapshot(Manifest manifest, List<SnapshotPolicy> policies, List<SnapshotChunk> chunks) {

    public static final String ENV_DIR = "AI_EVAL_SNAPSHOT_DIR";
    /** 운영 임베딩 차원 (policy_chunk_embedding.embedding vector(1536)). 단위 테스트 표본은 차원을 줄여 쓴다 */
    public static final int PRODUCTION_DIMENSIONS = 1536;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    public PolicySearchSnapshot {
        policies = List.copyOf(policies);
        chunks = List.copyOf(chunks);
    }

    /** 환경 변수의 경로에서 읽는다. 변수가 없으면 empty (스냅샷이 필요한 평가는 skip) */
    public static Optional<PolicySearchSnapshot> fromEnv() {
        String dir = System.getenv(ENV_DIR);
        if (dir == null || dir.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(load(Path.of(dir)));
    }

    /**
     * @throws IllegalStateException 건수가 manifest와 다르거나, ID가 중복되거나, 임베딩 차원이 섞여 있거나,
     *                               없는 정책·청크를 가리키는 행이 있을 때
     */
    public static PolicySearchSnapshot load(Path dir) {
        Manifest manifest = readLines(dir.resolve("manifest.jsonl"), Manifest.class).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("manifest.jsonl이 비어 있습니다: " + dir));
        List<SnapshotPolicy> policies = readLines(dir.resolve("policy.jsonl"), SnapshotPolicy.class);
        List<ChunkRow> chunkRows = readLines(dir.resolve("chunk.jsonl"), ChunkRow.class);
        List<EmbeddingRow> embeddingRows = readLines(dir.resolve("embedding.jsonl"), EmbeddingRow.class);

        check(policies.size() == manifest.policyCount(), "정책 수가 manifest와 다릅니다: " + policies.size());
        check(chunkRows.size() == manifest.chunkCount(), "청크 수가 manifest와 다릅니다: " + chunkRows.size());
        check(embeddingRows.size() == manifest.embeddingCount(), "임베딩 수가 manifest와 다릅니다: " + embeddingRows.size());

        Set<UUID> policyIds = new HashSet<>();
        for (SnapshotPolicy policy : policies) {
            check(policyIds.add(policy.policyId()), "정책 ID 중복: " + policy.policyId());
        }
        Map<UUID, float[]> vectors = new HashMap<>();
        Integer dimensions = null;
        for (EmbeddingRow row : embeddingRows) {
            float[] vector = parseVector(row.embedding());
            check(dimensions == null || dimensions == vector.length, "임베딩 차원이 섞여 있습니다: " + row.chunkId());
            dimensions = vector.length;
            check(vectors.put(row.chunkId(), vector) == null, "임베딩 청크 ID 중복: " + row.chunkId());
        }

        Set<UUID> chunkIds = new HashSet<>();
        List<SnapshotChunk> chunks = new ArrayList<>(chunkRows.size());
        for (ChunkRow row : chunkRows) {
            check(chunkIds.add(row.chunkId()), "청크 ID 중복: " + row.chunkId());
            check(policyIds.contains(row.policyId()), "없는 정책의 청크: " + row.chunkId());
            chunks.add(new SnapshotChunk(row.chunkId(), row.policyId(), row.sectionLabel(), row.seq(), row.content(),
                    vectors.get(row.chunkId())));
        }
        for (UUID chunkId : vectors.keySet()) {
            check(chunkIds.contains(chunkId), "없는 청크의 임베딩: " + chunkId);
        }
        return new PolicySearchSnapshot(manifest, policies, chunks);
    }

    /** 임베딩 차원. 임베딩이 하나도 없으면 0 */
    public int dimensions() {
        return chunks.stream().filter(c -> c.vector() != null).mapToInt(c -> c.vector().length).findFirst().orElse(0);
    }

    /** 운영 검색 JOIN에서 빠지는 청크 수 (임베딩 없음) */
    public long chunksWithoutEmbedding() {
        return chunks.stream().filter(c -> c.vector() == null).count();
    }

    /**
     * COPY 텍스트 형식의 이스케이프를 푼다. COPY TO는 \\, \b, \f, \n, \r, \t, \v만 쓰고 8진수·16진수 표기는 쓰지 않는다
     * (PostgreSQL COPY 문서 "File Formats > Text Format").
     */
    static String decodeCopyText(String line) {
        if (line.indexOf('\\') < 0) {
            return line;
        }
        StringBuilder out = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c != '\\' || i + 1 == line.length()) {
                out.append(c);
                continue;
            }
            char next = line.charAt(++i);
            out.append(switch (next) {
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'v' -> '\u000B';
                default -> next; // \\ 와 그 밖의 문자는 문자 그대로
            });
        }
        return out.toString();
    }

    /** pgvector 텍스트 형식 "[0.1,0.2,...]" */
    static float[] parseVector(String text) {
        String trimmed = text == null ? "" : text.strip();
        check(trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length() > 2, "vector 형식이 아닙니다: " + text);
        String[] parts = trimmed.substring(1, trimmed.length() - 1).split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].strip());
        }
        return vector;
    }

    private static <T> List<T> readLines(Path file, Class<T> type) {
        try {
            List<T> rows = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    rows.add(MAPPER.readValue(decodeCopyText(line), type));
                }
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException("스냅샷 파일을 읽을 수 없습니다: " + file, e);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("스냅샷 검증 실패: " + message);
        }
    }

    public record Manifest(String exportedAt, int policyCount, int chunkCount, int embeddingCount,
                           List<String> embeddingModels) {
    }

    /** policy 테이블 1행 (원문 컬럼 포함). 혜택(benefit) 컬럼은 검색·답변에 쓰이지 않아 내보내지 않는다 */
    public record SnapshotPolicy(UUID policyId, String source, String externalId, String name, String description,
                                 String policyType, String region, Integer minAge, Integer maxAge, Long incomeLimit,
                                 Long benefitAmount, String supportContent, String eligibilityDetail,
                                 String exclusionCriteria, String incomeCondition, String ageCondition,
                                 String applyMethod, String screeningMethod, String requiredDocuments,
                                 String applyPeriod, String businessPeriod, String referenceUrl, String applyUrl,
                                 String contactInfo, String etcNote) {

        public PolicyKey key() {
            return new PolicyKey(source, externalId);
        }

        /** 저장 전 Policy. 정책 ID는 H2 저장 시 새로 생성된다 (스냅샷 ID와 다름) */
        public Policy toPolicy() {
            PolicyText text = PolicyText.builder()
                    .supportContent(supportContent)
                    .eligibilityDetail(eligibilityDetail)
                    .exclusionCriteria(exclusionCriteria)
                    .incomeCondition(incomeCondition)
                    .ageCondition(ageCondition)
                    .applyMethod(applyMethod)
                    .screeningMethod(screeningMethod)
                    .requiredDocuments(requiredDocuments)
                    .applyPeriod(applyPeriod)
                    .businessPeriod(businessPeriod)
                    .referenceUrl(referenceUrl)
                    .applyUrl(applyUrl)
                    .contactInfo(contactInfo)
                    .etcNote(etcNote)
                    .build();
            return Policy.create(name, description, minAge, maxAge, incomeLimit, benefitAmount,
                    PolicyType.valueOf(policyType), region, externalId, source, text);
        }
    }

    /**
     * @param vector 청크 임베딩. 임베딩이 없으면 null
     */
    public record SnapshotChunk(UUID chunkId, UUID policyId, String sectionLabel, int seq, String content,
                                float[] vector) {
    }

    record ChunkRow(UUID chunkId, UUID policyId, String sectionLabel, int seq, String content, String sourceHash) {
    }

    record EmbeddingRow(UUID chunkId, String model, String embedding) {
    }
}
