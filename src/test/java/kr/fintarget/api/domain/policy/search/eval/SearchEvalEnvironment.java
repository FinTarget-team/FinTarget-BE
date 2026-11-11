package kr.fintarget.api.domain.policy.search.eval;

import kr.fintarget.api.domain.ai.client.AiEmbeddingClient;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 스냅샷이 필요한 평가 실행기의 공통 준비 (#184): 스냅샷 읽기 → 검증 → H2 적재 → 질문 임베딩 캐시 준비.
 *
 * <p>캐시에 없는 질문이 있으면 OPENAI_API_KEY와 RUN_AI_EVAL=true일 때만 임베딩 API를 호출해 캐시를 채운다.
 * 둘 중 하나라도 없으면 실패한다 (조용히 skip하면 일부 문항만 평가된 결과가 나올 수 있어서).
 */
@Slf4j
public record SearchEvalEnvironment(Path snapshotDir, PolicySearchSnapshot snapshot, PolicySearchSnapshotLoader.Loaded loaded,
                                    QuestionEmbeddingCache cache) {

    public static final Path OUTPUT_ROOT = Path.of("build", "ai-eval");

    /**
     * @param repository H2 정책 저장소 (@DataJpaTest)
     * @param questions  임베딩이 필요한 질문 전체
     * @param knownKeys  평가셋이 가리키는 정책 키. 스냅샷에 없는 키가 있으면 실패한다
     */
    public static SearchEvalEnvironment open(PolicyRepository repository, Collection<String> questions,
                                             Collection<String> knownKeys) {
        String dir = System.getenv(PolicySearchSnapshot.ENV_DIR);
        if (dir == null || dir.isBlank()) {
            throw new IllegalStateException(PolicySearchSnapshot.ENV_DIR + "가 없습니다");
        }
        Path snapshotDir = Path.of(dir);
        PolicySearchSnapshot snapshot = PolicySearchSnapshot.load(snapshotDir);
        List<String> models = snapshot.manifest().embeddingModels();
        if (models == null || models.size() != 1) {
            throw new IllegalStateException("스냅샷 임베딩 모델이 하나여야 합니다: " + models);
        }
        if (snapshot.chunksWithoutEmbedding() > 0) {
            log.warn("임베딩이 없는 청크 {}건은 검색 대상에서 빠집니다 (운영과 같음)", snapshot.chunksWithoutEmbedding());
        }

        List<String> unknown = knownKeys.stream()
                .filter(key -> snapshot.policies().stream().noneMatch(p -> p.key().toString().equals(key))).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("스냅샷에 없는 정책 키 (스냅샷을 바꿨다면 라벨을 다시 확인): " + unknown);
        }

        PolicySearchSnapshotLoader.Loaded loaded = PolicySearchSnapshotLoader.load(snapshot, repository);

        String cachePath = System.getenv(QuestionEmbeddingCache.ENV_PATH);
        Path cacheFile = cachePath == null || cachePath.isBlank()
                ? snapshotDir.resolve(QuestionEmbeddingCache.DEFAULT_FILE) : Path.of(cachePath);
        QuestionEmbeddingCache cache = QuestionEmbeddingCache.open(cacheFile, models.getFirst());
        List<String> missing = cache.missing(questions);
        if (!missing.isEmpty()) {
            if (!EvalAiContext.liveCallsAllowed()) {
                throw new IllegalStateException("질문 임베딩 캐시에 없는 질문 " + missing.size() + "건. "
                        + EvalAiContext.ENV_API_KEY + "와 " + EvalAiContext.ENV_RUN + "=true로 한 번 실행해 캐시를 채우세요: " + cacheFile);
            }
            EvalAiContext.run(context -> {
                int added = cache.fill(missing, context.getBean(AiEmbeddingClient.class));
                log.info("질문 임베딩 {}건을 캐시에 추가: {}", added, cacheFile.toAbsolutePath());
            });
        }
        if (cache.dimensions() != 0 && cache.dimensions() != snapshot.dimensions()) {
            throw new IllegalStateException("질문 임베딩 차원이 스냅샷과 다릅니다: " + cache.dimensions() + " != " + snapshot.dimensions());
        }
        return new SearchEvalEnvironment(snapshotDir, snapshot, loaded, cache);
    }

    /** 리포트 머리말용 실행 정보 */
    public Map<String, Object> context() {
        // 값은 모두 문자열 (results.json에 그대로 쓴다)
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("실행", LocalDateTime.now().withNano(0).toString());
        context.put("스냅샷", snapshotDir.toAbsolutePath() + " (내보낸 시각 " + snapshot.manifest().exportedAt() + ")");
        context.put("정책·청크·임베딩", snapshot.policies().size() + " / " + snapshot.chunks().size() + " / "
                + (snapshot.chunks().size() - snapshot.chunksWithoutEmbedding()));
        context.put("임베딩 모델", cache.model() + " (" + snapshot.dimensions() + "차원)");
        context.put("질문 임베딩 캐시", cache.file() == null ? "(메모리)" : cache.file().toAbsolutePath().toString());
        return context;
    }

    /** build/ai-eval/{prefix}-{실행 시각}/ */
    public static Path outputDir(String prefix) {
        return OUTPUT_ROOT.resolve(prefix + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
    }
}
