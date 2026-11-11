package kr.fintarget.api.domain.policy.search.eval;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.search.PolicySearchHit;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.SearchCase;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchSnapshot.SnapshotChunk;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchSnapshot.SnapshotPolicy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 정답 라벨링용 풀 (#184). 문항마다 후보 정책 중 두 방식의 상위 결과를 합쳐, 사람이 이 목록 안에서 정답을 고르게 한다.
 * <ul>
 *   <li>벡터: 하한 없는 운영 검색 상위 N건 (기본 {@value #VECTOR_TOP}, {@value #ENV_VECTOR_TOP}로 조정)</li>
 *   <li>키워드: 질문 낱말이 정책 원문(청크 전체)에 들어 있는 정도 상위 {@value #KEYWORD_TOP}건. 벡터 검색이 놓친 정답을 찾기 위한 것</li>
 * </ul>
 * 두 방식 모두 프로필 후보 정책 안에서만 찾는다 (정답은 그 프로필이 신청할 수 있는 정책만이라서).
 * Recall 대상 유형(무관·모호 제외)만 만든다.
 *
 * <p>키워드 점수: 질문을 낱말로 나눠(조사 제거, 2자 이상) 공백을 지운 원문에 들어 있으면 idf = ln(1 + 후보 수 / 그 낱말이 든 후보 수)를 더한다.
 * 정책명에도 있으면 한 번 더 더한다. 흔한 낱말(청년, 지원)은 점수가 작다.
 */
public final class PolicySearchPool {

    public static final int VECTOR_TOP = 20;
    public static final int KEYWORD_TOP = 10;

    public static final String ENV_VECTOR_TOP = "AI_EVAL_POOL_VECTOR_TOP";
    public static final String ENV_KEYWORD_TOP = "AI_EVAL_POOL_KEYWORD_TOP";
    static final int SNIPPET_LENGTH = 160;

    private static final Pattern SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");
    // 긴 것부터 확인한다
    private static final List<String> PARTICLES = List.of("에서는", "으로는", "에게서", "에서", "으로", "에게", "까지", "부터",
            "이랑", "하고", "처럼", "은", "는", "이", "가", "을", "를", "에", "의", "도", "로", "와", "과", "만", "요");

    private final PolicySearchEvalRunner runner;
    private final Map<PolicyKey, String> texts = new HashMap<>();
    private final Map<PolicyKey, String> firstChunks = new HashMap<>();
    private final int chunkCount;

    /**
     * @param snapshot 키워드 검색 원문 (임베딩 없는 청크도 포함)
     */
    public PolicySearchPool(PolicySearchEvalRunner runner, PolicySearchSnapshot snapshot) {
        this.runner = runner;
        this.chunkCount = snapshot.chunks().size();
        Map<UUID, PolicyKey> keyBySnapshotId = new HashMap<>();
        for (SnapshotPolicy policy : snapshot.policies()) {
            keyBySnapshotId.put(policy.policyId(), policy.key());
        }
        Map<PolicyKey, StringBuilder> joined = new LinkedHashMap<>();
        List<SnapshotChunk> bySeq = snapshot.chunks().stream().sorted(Comparator.comparingInt(SnapshotChunk::seq)).toList();
        for (SnapshotChunk chunk : bySeq) {
            PolicyKey key = keyBySnapshotId.get(chunk.policyId());
            joined.computeIfAbsent(key, k -> new StringBuilder()).append(chunk.content()).append('\n');
            firstChunks.putIfAbsent(key, chunk.content());
        }
        joined.forEach((key, text) -> texts.put(key, normalize(text.toString())));
    }

    /**
     * 환경 변수로 풀 크기를 정한다 ({@value #ENV_VECTOR_TOP}, {@value #ENV_KEYWORD_TOP}).
     *
     * <p>풀에는 거리 상한이 없다({@link SearchConfig#WIDE}의 하한 2.0은 코사인 거리 최대값이라 아무것도 자르지 않는다).
     * 실제 제약은 <b>벡터 상위 몇 건까지 담는지</b>다. 구어체 질문은 KINFA 대출이 상위를 차지해
     * 정답이 20위 근처로 밀리므로(2026-11-06 풀: benefit-01의 "2026 청년 월세 지원"이 벡터 20위),
     * 라벨링 전에 이 값을 키워 다시 만드는 것이 안전하다.
     */
    public List<CasePool> buildFromEnv() {
        return build(intEnv(ENV_VECTOR_TOP, VECTOR_TOP), intEnv(ENV_KEYWORD_TOP, KEYWORD_TOP));
    }

    private static int intEnv(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        int parsed = Integer.parseInt(value.trim());
        if (parsed < 1) {
            throw new IllegalArgumentException(name + "은 1 이상이어야 합니다: " + parsed);
        }
        return parsed;
    }

    public List<CasePool> build(int vectorTop, int keywordTop) {
        List<CasePool> pools = new ArrayList<>();
        for (SearchCase c : runner.evalSet().cases()) {
            if (c.type().scoredForRecall()) {
                pools.add(build(c, vectorTop, keywordTop));
            }
        }
        return pools;
    }

    CasePool build(SearchCase c, int vectorTop, int keywordTop) {
        List<Policy> candidates = runner.candidatesOf(c);
        Map<PolicyKey, Policy> candidateByKey = new LinkedHashMap<>();
        candidates.forEach(policy -> candidateByKey.put(runner.loaded().keyOf(policy), policy));

        Map<PolicyKey, Item.Builder> items = new LinkedHashMap<>();
        // SearchConfig.WIDE(k=20)를 그대로 쓰면 vectorTop을 키워도 검색이 20건에서 끊긴다.
        // 청크 조회 상한(k×배수)에 걸리지 않도록 배수는 전체 청크 수 이상으로 둔다
        SearchConfig config = new SearchConfig(Math.max(1, vectorTop), SearchConfig.WIDE.maxDistance(),
                Math.max(SearchConfig.WIDE.chunkMultiplier(), chunkCount));
        List<PolicySearchHit> hits = runner.search(c, config);
        for (int i = 0; i < Math.min(vectorTop, hits.size()); i++) {
            PolicySearchHit hit = hits.get(i);
            PolicyKey key = runner.loaded().keyOf(hit.policy());
            Item.Builder builder = items.computeIfAbsent(key, k -> new Item.Builder(k, hit.policy()));
            builder.vectorRank = i + 1;
            builder.distance = hit.distance();
            builder.snippet = snippet(hit.content());
        }

        List<String> tokens = tokens(c.question());
        List<KeywordScore> keywordScores = keywordScores(tokens, candidateByKey);
        for (int i = 0; i < Math.min(keywordTop, keywordScores.size()); i++) {
            KeywordScore score = keywordScores.get(i);
            Item.Builder builder = items.computeIfAbsent(score.key(), k -> new Item.Builder(k, candidateByKey.get(k)));
            builder.keywordRank = i + 1;
            builder.keywordScore = score.score();
            builder.matchedTokens = score.matched();
            if (builder.snippet == null) {
                builder.snippet = snippet(firstChunks.get(score.key()));
            }
        }
        items.values().forEach(builder -> builder.eligible = runner.eligible(c, builder.policy));

        Set<PolicyKey> labeled = Set.copyOf(c.relevant());
        List<Item> merged = items.values().stream().map(builder -> builder.build(labeled.contains(builder.key)))
                .sorted(Comparator.comparingInt(Item::bestRank)
                        .thenComparingInt(item -> item.vectorRank() == null ? Integer.MAX_VALUE : item.vectorRank()))
                .toList();
        List<PolicyKey> labeledOutsidePool = c.relevant().stream().filter(key -> !items.containsKey(key))
                .filter(candidateByKey::containsKey).toList();
        List<PolicyKey> labeledNotCandidate = c.relevant().stream().filter(key -> !candidateByKey.containsKey(key)).toList();
        return new CasePool(c, candidates.size(), tokens, merged, labeledOutsidePool, labeledNotCandidate);
    }

    private List<KeywordScore> keywordScores(List<String> tokens, Map<PolicyKey, Policy> candidates) {
        if (tokens.isEmpty() || candidates.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (String token : tokens) {
            int df = (int) candidates.keySet().stream().filter(key -> texts.getOrDefault(key, "").contains(token)).count();
            documentFrequency.put(token, df);
        }
        List<KeywordScore> scores = new ArrayList<>();
        candidates.forEach((key, policy) -> {
            String text = texts.getOrDefault(key, "");
            String name = normalize(policy.getName());
            double score = 0;
            List<String> matched = new ArrayList<>();
            for (String token : tokens) {
                if (!text.contains(token) && !name.contains(token)) {
                    continue;
                }
                double idf = Math.log(1 + (double) candidates.size() / Math.max(1, documentFrequency.get(token)));
                score += idf * (name.contains(token) ? 2 : 1);
                matched.add(token);
            }
            if (score > 0) {
                scores.add(new KeywordScore(key, score, List.copyOf(matched)));
            }
        });
        scores.sort(Comparator.comparingDouble(KeywordScore::score).reversed()
                .thenComparing(score -> score.key().toString()));
        return scores;
    }

    /** 질문을 낱말로 나눈다: 기호·공백으로 자르고 끝의 조사 하나를 뗀 뒤 2자 이상만 (중복 제거) */
    static List<String> tokens(String question) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String word : SPLIT.split(question == null ? "" : question)) {
            String token = word;
            for (String particle : PARTICLES) {
                if (token.length() > particle.length() + 1 && token.endsWith(particle)) {
                    token = token.substring(0, token.length() - particle.length());
                    break;
                }
            }
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return List.copyOf(tokens);
    }

    static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    /** 청크 첫 줄(정책명 머리말)을 빼고 줄바꿈을 " / "로 바꿔 앞부분만 */
    static String snippet(String content) {
        if (content == null) {
            return "";
        }
        String body = content.startsWith("정책명:") && content.indexOf('\n') > 0
                ? content.substring(content.indexOf('\n') + 1) : content;
        String oneLine = Arrays.stream(body.split("\n")).map(String::strip).filter(s -> !s.isEmpty())
                .reduce((a, b) -> a + " / " + b).orElse("");
        return oneLine.length() <= SNIPPET_LENGTH ? oneLine : oneLine.substring(0, SNIPPET_LENGTH) + "…";
    }

    record KeywordScore(PolicyKey key, double score, List<String> matched) {
    }

    /**
     * @param candidateCount       프로필 후보 정책 수
     * @param tokens               키워드 검색에 쓴 낱말
     * @param items                풀 (벡터·키워드 중 높은 순위 순)
     * @param labeledOutsidePool   이미 정답으로 적었지만 풀 밖인 후보 정책 (검색이 모두 놓침)
     * @param labeledNotCandidate  정답으로 적었지만 프로필 후보가 아닌 정책 (조건에 안 맞음 → 라벨 오류 의심)
     */
    public record CasePool(SearchCase evalCase, int candidateCount, List<String> tokens, List<Item> items,
                           List<PolicyKey> labeledOutsidePool, List<PolicyKey> labeledNotCandidate) {
    }

    /**
     * @param vectorRank  벡터 순위 (풀에 벡터로 안 들어왔으면 null)
     * @param distance    벡터 거리 (vectorRank가 null이면 null)
     * @param keywordRank 키워드 순위 (null이면 키워드로 안 들어옴)
     * @param eligible    {@link EligibilityCheck}로 다시 계산한 조건 통과 여부
     * @param labeled     현재 평가셋에 정답으로 적혀 있음
     * @param snippet     가장 가까운 청크(키워드로만 들어왔으면 첫 청크)의 앞부분
     */
    public record Item(PolicyKey key, String name, String region, Integer vectorRank, Double distance, Integer keywordRank,
                       Double keywordScore, List<String> matchedTokens, boolean eligible, boolean labeled, String snippet) {

        int bestRank() {
            return Math.min(vectorRank == null ? Integer.MAX_VALUE : vectorRank,
                    keywordRank == null ? Integer.MAX_VALUE : keywordRank);
        }

        static final class Builder {
            private final PolicyKey key;
            private final Policy policy;
            private Integer vectorRank;
            private Double distance;
            private Integer keywordRank;
            private Double keywordScore;
            private List<String> matchedTokens = List.of();
            private boolean eligible = true;
            private String snippet;

            Builder(PolicyKey key, Policy policy) {
                this.key = key;
                this.policy = policy;
            }

            Item build(boolean labeled) {
                return new Item(key, policy.getName(), policy.getRegion(), vectorRank, distance, keywordRank, keywordScore,
                        matchedTokens, eligible, labeled, snippet);
            }
        }
    }
}
