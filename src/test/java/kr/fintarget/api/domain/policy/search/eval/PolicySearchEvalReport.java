package kr.fintarget.api.domain.policy.search.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalRunner.ConfigResult;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalRunner.Report;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.CaseType;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.SearchCase;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchScorer.CaseScore;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchScorer.ResultItem;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchScorer.Summary;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchScorer.TypeSummary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 검색 평가 결과 파일 (#184). results.json(기계용)과 report.md(사람용)를 쓴다.
 * 평가 질문과 정책명만 들어가며 사용자 데이터는 없다.
 */
public final class PolicySearchEvalReport {

    static final int CASE_TOP = 10;

    private PolicySearchEvalReport() {
    }

    /**
     * @param context 리포트 머리말에 넣을 실행 정보 (스냅샷 시각, 건수, 모델 등)
     */
    public static void write(Path dir, PolicySearchEvalSet evalSet, Report report, Map<String, Object> context)
            throws IOException {
        Files.createDirectories(dir);
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(dir.resolve("results.json").toFile(), json(report, context));
        Files.writeString(dir.resolve("report.md"), markdown(evalSet, report, context), StandardCharsets.UTF_8);
    }

    static Map<String, Object> json(Report report, Map<String, Object> context) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("context", context);
        List<Map<String, Object>> configs = new ArrayList<>();
        for (ConfigResult r : report.configs()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("k", r.config().k());
            row.put("maxDistance", r.config().maxDistance());
            row.put("chunkMultiplier", r.config().chunkMultiplier());
            row.put("production", r.config().isProduction());
            row.put("recallAtK", r.recallAtK());
            row.put("wideRecallAtK", r.wideRecallAtK());
            row.put("loss", r.loss());
            row.put("mrr", r.summary().mrr());
            row.put("averageResults", r.averageResults());
            row.put("scoredEmpty", r.scoredEmpty());
            row.put("unrelatedEmpty", r.summary().unrelatedEmpty());
            row.put("violations", r.summary().violations());
            row.put("kinfaSkewedCases", r.summary().kinfaSkewedCases());
            row.put("meetsTarget", r.meetsTarget());
            configs.add(row);
        }
        json.put("configs", configs);
        json.put("wideSummary", summaryJson(report.wideSummary()));
        List<Map<String, Object>> cases = new ArrayList<>();
        for (CaseScore score : report.wideScores()) {
            SearchCase c = score.evalCase();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.id());
            row.put("type", c.type());
            row.put("profile", c.profile());
            row.put("question", c.question());
            row.put("relevant", c.relevant().stream().map(PolicyKey::toString).toList());
            row.put("scored", score.scored());
            row.put("recall", score.recall());
            row.put("firstRelevantRank", score.firstRelevantRank());
            row.put("results", report.wideResults().get(c.id()).stream().map(item -> Map.of(
                    "key", item.key().toString(), "name", item.name(), "distance", item.distance(),
                    "eligible", item.eligible())).toList());
            cases.add(row);
        }
        json.put("cases", cases);
        return json;
    }

    private static Map<String, Object> summaryJson(Summary s) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("cases", s.cases());
        json.put("scoredCases", s.scoredCases());
        json.put("unlabeledCases", s.unlabeledCases());
        json.put("recall", s.recall());
        json.put("nameRecall", s.nameRecall());
        json.put("mrr", s.mrr());
        json.put("violations", s.violations());
        json.put("violationCases", s.violationCases());
        json.put("regionFallbacks", s.regionFallbacks());
        json.put("kinfaSkewedCases", s.kinfaSkewedCases());
        json.put("relevantDistanceHistogram", s.relevantDistanceHistogram());
        json.put("otherDistanceHistogram", s.otherDistanceHistogram());
        json.put("unrelatedTop1Histogram", s.unrelatedTop1Histogram());
        return json;
    }

    static String markdown(PolicySearchEvalSet evalSet, Report report, Map<String, Object> context) {
        Summary wide = report.wideSummary();
        StringBuilder md = new StringBuilder("# 정책 검색 평가 리포트 (#184)\n\n");
        md.append("- 평가셋: `").append(PolicySearchEvalSet.RESOURCE).append("` (").append(wide.cases())
                .append("문항, 정답 있는 문항 ").append(wide.scoredCases()).append(")\n");
        context.forEach((key, value) -> md.append("- ").append(key).append(": ").append(value).append('\n'));
        if (wide.unlabeledCases() > 0) {
            md.append("\n> 정답(relevant)이 비어 있는 문항 ").append(wide.unlabeledCases())
                    .append("건은 Recall·MRR에서 빠졌습니다. 풀 결과로 라벨링한 뒤 다시 실행하세요.\n");
        }

        md.append("\n## 설정값 비교\n\n")
                .append("잠정 목표: Recall@k ≥ 0.90, 하한 손실 ≤ 2%p, 조건 위반 0. ★ = 현재 운영 기본값\n\n")
                .append("| k | 하한 | 배수 | Recall@k | 하한 없는 Recall@k | 손실(%p) | MRR | 평균 결과 수 | 정답 문항 0건 | 무관 0건 | 조건 위반 | KINFA 쏠림 | 목표 |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        report.configs().stream()
                .sorted(Comparator.comparingInt((ConfigResult r) -> r.config().k())
                        .thenComparingDouble(r -> r.config().maxDistance())
                        .thenComparingInt(r -> r.config().chunkMultiplier()))
                .forEach(r -> md.append(String.format(Locale.ROOT,
                        "| %d%s | %.2f | %d | %.3f | %.3f | %.1f | %.3f | %.1f | %d | %d | %d | %d | %s |%n",
                        r.config().k(), r.config().isProduction() ? " ★" : "", r.config().maxDistance(),
                        r.config().chunkMultiplier(), r.recallAtK(), r.wideRecallAtK(), r.loss() * 100,
                        r.summary().mrr(), r.averageResults(), r.scoredEmpty(), r.summary().unrelatedEmpty(),
                        r.summary().violations(), r.summary().kinfaSkewedCases().size(), r.meetsTarget() ? "충족" : "-")));

        md.append("\n## 하한 없는 검색 (").append(SearchConfig.WIDE.label()).append(")\n\n");
        md.append("| 지표 | @3 | @5 | @10 |\n|---|---|---|---|\n");
        md.append("| Recall | ").append(ks(wide.recall())).append(" |\n");
        md.append("| 이름 단위 Recall | ").append(ks(wide.nameRecall())).append(" |\n");
        md.append(String.format(Locale.ROOT, "%n- MRR: %.3f%n- 조건 위반: %d건 %s%n- 지역 전국 처리(코드 아닌 지역 값): %d건%n- KINFA 쏠림 문항: %s%n",
                wide.mrr(), wide.violations(), wide.violationCases(), wide.regionFallbacks(), wide.kinfaSkewedCases()));

        md.append("\n### 유형별\n\n| 유형 | 문항 | 정답 있는 문항 | Recall@5 | MRR |\n|---|---|---|---|---|\n");
        for (Map.Entry<CaseType, TypeSummary> entry : wide.byType().entrySet()) {
            TypeSummary t = entry.getValue();
            md.append(String.format(Locale.ROOT, "| %s | %d | %d | %s | %s |%n", entry.getKey(), t.cases(), t.scoredCases(),
                    t.recallAt5() == null ? "-" : String.format(Locale.ROOT, "%.3f", t.recallAt5()),
                    t.mrr() == null ? "-" : String.format(Locale.ROOT, "%.3f", t.mrr())));
        }

        md.append("\n### 거리 분포 (0.05 구간, 하한 정할 때 참고)\n\n| 구간 | 정답 | 정답 아님 | 무관 문항 1위 |\n|---|---|---|---|\n");
        Set<String> bins = new TreeSet<>();
        bins.addAll(wide.relevantDistanceHistogram().keySet());
        bins.addAll(wide.otherDistanceHistogram().keySet());
        bins.addAll(wide.unrelatedTop1Histogram().keySet());
        for (String bin : bins) {
            md.append("| ").append(bin).append(" | ").append(wide.relevantDistanceHistogram().getOrDefault(bin, 0))
                    .append(" | ").append(wide.otherDistanceHistogram().getOrDefault(bin, 0))
                    .append(" | ").append(wide.unrelatedTop1Histogram().getOrDefault(bin, 0)).append(" |\n");
        }

        md.append("\n## 문항별 (하한 없는 상위 ").append(CASE_TOP).append("건)\n");
        for (CaseScore score : report.wideScores()) {
            SearchCase c = score.evalCase();
            Set<PolicyKey> relevant = Set.copyOf(c.relevant());
            md.append("\n### ").append(c.id()).append(" (").append(c.type()).append(", ").append(c.profile()).append(")\n\n")
                    .append("- 질문: ").append(c.question()).append('\n');
            if (c.note() != null) {
                md.append("- 메모: ").append(c.note()).append('\n');
            }
            md.append("- 정답: ").append(c.relevant().isEmpty() ? "(없음)" : c.relevant().stream().map(k -> "`" + k + "`")
                    .collect(Collectors.joining(", "))).append('\n');
            if (score.scored()) {
                md.append(String.format(Locale.ROOT, "- Recall@5 %.2f, 첫 정답 순위 %s%n", score.recall().get(5),
                        score.firstRelevantRank() == null ? "없음" : score.firstRelevantRank()));
            }
            List<ResultItem> items = report.wideResults().get(c.id());
            if (items.isEmpty()) {
                md.append("\n결과 없음 (후보 정책 없음)\n");
                continue;
            }
            md.append("\n| 순위 | 거리 | 정답 | 정책 | 키 |\n|---|---|---|---|---|\n");
            for (int i = 0; i < Math.min(CASE_TOP, items.size()); i++) {
                ResultItem item = items.get(i);
                md.append(String.format(Locale.ROOT, "| %d | %.4f | %s | %s | `%s` |%n", i + 1, item.distance(),
                        (relevant.contains(item.key()) ? "O" : "") + (item.eligible() ? "" : " 조건 위반"),
                        cell(item.name()), cell(item.key().toString())));
            }
            List<PolicyKey> missing = c.relevant().stream()
                    .filter(key -> items.stream().noneMatch(item -> item.key().equals(key))).toList();
            if (!missing.isEmpty()) {
                md.append("\n상위 ").append(SearchConfig.WIDE.k()).append("건에 없는 정답: ")
                        .append(missing.stream().map(k -> "`" + k + "`").collect(Collectors.joining(", "))).append('\n');
            }
        }
        return md.toString();
    }

    // KINFA 외부 ID에 '|'가 들어 있어 표 칸 안에서는 이스케이프한다 (정답 줄·results.json은 원래 키)
    static String cell(String text) {
        return text == null ? "" : text.replace("|", "\\|").replace("\n", " ");
    }

    private static String ks(Map<Integer, Double> byK) {
        return PolicySearchScorer.RECALL_KS.stream()
                .map(k -> byK.containsKey(k) ? String.format(Locale.ROOT, "%.3f", byK.get(k)) : "-")
                .collect(Collectors.joining(" | "));
    }
}
