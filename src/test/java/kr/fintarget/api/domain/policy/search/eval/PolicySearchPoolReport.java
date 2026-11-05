package kr.fintarget.api.domain.policy.search.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.SearchCase;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchPool.CasePool;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchPool.Item;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 라벨링용 풀 결과 파일 (#184). pool.md(사람이 보고 정답을 고르는 파일)와 pool.json(같은 내용, 기계용)을 쓴다.
 */
public final class PolicySearchPoolReport {

    private PolicySearchPoolReport() {
    }

    public static void write(Path dir, List<CasePool> pools, Map<String, Object> context) throws IOException {
        Files.createDirectories(dir);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("context", context);
        json.put("cases", pools.stream().map(PolicySearchPoolReport::json).toList());
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(dir.resolve("pool.json").toFile(), json);
        Files.writeString(dir.resolve("pool.md"), markdown(pools, context), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> json(CasePool pool) {
        SearchCase c = pool.evalCase();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", c.id());
        json.put("type", c.type());
        json.put("profile", c.profile());
        json.put("question", c.question());
        json.put("candidateCount", pool.candidateCount());
        json.put("tokens", pool.tokens());
        json.put("relevant", c.relevant().stream().map(PolicyKey::toString).toList());
        json.put("labeledOutsidePool", pool.labeledOutsidePool().stream().map(PolicyKey::toString).toList());
        json.put("labeledNotCandidate", pool.labeledNotCandidate().stream().map(PolicyKey::toString).toList());
        json.put("items", pool.items().stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", item.key().toString());
            row.put("name", item.name());
            row.put("region", item.region());
            row.put("vectorRank", item.vectorRank());
            row.put("distance", item.distance());
            row.put("keywordRank", item.keywordRank());
            row.put("keywordScore", item.keywordScore());
            row.put("matchedTokens", item.matchedTokens());
            row.put("eligible", item.eligible());
            row.put("labeled", item.labeled());
            row.put("snippet", item.snippet());
            return row;
        }).toList());
        return json;
    }

    static String markdown(List<CasePool> pools, Map<String, Object> context) {
        StringBuilder md = new StringBuilder("# 정책 검색 정답 라벨링 풀 (#184)\n\n");
        context.forEach((key, value) -> md.append("- ").append(key).append(": ").append(value).append('\n'));
        md.append("""

                ## 라벨링 방법

                1. 문항마다 아래 목록에서 질문에 맞는 정책을 고른다. 목록은 그 프로필이 신청할 수 있는 후보 정책 중
                   벡터 검색 상위 %d건과 키워드 검색 상위 %d건을 합친 것이다.
                2. 고른 정책의 키(백틱 안 문자열)를 `src/test/resources/ai-eval/policy-search-cases.json` 문항의 `relevant`에 넣는다.
                3. 같은 이름의 다른 지역 정책은 후보에 없으므로 목록에 나오지 않는다. 목록에 없는 정책을 정답으로 넣지 않는다.
                4. 판단 근거를 `note`에 한 줄 적는다. 라벨을 넣은 뒤 풀을 다시 만들면 **[현재 정답]**이 표시된다.

                "조건 위반"은 평가기가 다시 계산한 나이·소득·지역 조건에 맞지 않는다는 뜻이다 (운영 필터와 어긋남, 보고 대상).
                """.formatted(PolicySearchPool.VECTOR_TOP, PolicySearchPool.KEYWORD_TOP));

        for (CasePool pool : pools) {
            SearchCase c = pool.evalCase();
            md.append("\n## ").append(c.id()).append(" (").append(c.type()).append(", ").append(c.profile()).append(")\n\n")
                    .append("- 질문: ").append(c.question()).append('\n');
            if (c.note() != null) {
                md.append("- 메모: ").append(c.note()).append('\n');
            }
            md.append("- 후보 정책 ").append(pool.candidateCount()).append("건, 키워드: ")
                    .append(pool.tokens().isEmpty() ? "(없음)" : String.join(", ", pool.tokens())).append('\n')
                    .append("- 현재 정답: ").append(keys(c.relevant())).append('\n');
            if (!pool.labeledNotCandidate().isEmpty()) {
                md.append("- 확인 필요: 정답인데 이 프로필의 후보가 아님 (조건에 맞지 않음) → ")
                        .append(keys(pool.labeledNotCandidate())).append('\n');
            }
            if (!pool.labeledOutsidePool().isEmpty()) {
                md.append("- 정답인데 풀 밖 (벡터·키워드 모두 놓침): ").append(keys(pool.labeledOutsidePool())).append('\n');
            }
            if (pool.items().isEmpty()) {
                md.append("\n풀이 비어 있음 (후보 정책 없음)\n");
                continue;
            }
            // 표 대신 목록으로 쓴다: KINFA 외부 ID에 '|'가 있어 표에 넣으면 이스케이프가 필요하고, 그러면 키를 그대로 복사할 수 없다
            md.append('\n');
            int index = 1;
            for (Item item : pool.items()) {
                md.append(index++).append(". ").append(item.labeled() ? "**[현재 정답]** " : "")
                        .append('`').append(item.key()).append("` ").append(oneLine(item.name()))
                        .append(" (").append(item.region() == null ? "전국" : item.region()).append(")\n")
                        .append("   - 벡터 ").append(item.vectorRank() == null ? "-"
                                : String.format(Locale.ROOT, "%d위 (거리 %.4f)", item.vectorRank(), item.distance()))
                        .append(" · 키워드 ").append(item.keywordRank() == null ? "-"
                                : item.keywordRank() + "위 (" + String.join(", ", item.matchedTokens()) + ")")
                        .append(item.eligible() ? "" : " · 조건 위반").append('\n')
                        .append("   - 원문: ").append(oneLine(item.snippet())).append('\n');
            }
        }
        return md.toString();
    }

    private static String keys(List<PolicyKey> keys) {
        return keys.isEmpty() ? "(없음)" : keys.stream().map(key -> "`" + key + "`").collect(Collectors.joining(", "));
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replace("\n", " ");
    }
}
