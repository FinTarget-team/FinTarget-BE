package kr.fintarget.api.domain.policy.rag.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalRunner.Result;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalSet.Category;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalSet.RagCase;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagScorer.Summary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 답변 평가 결과 파일 (#184). results.json(모델 원문 답변 포함)과 report.md(자동 채점 + 사람 검수용)를 쓴다.
 * 질문·답변 본문은 이 파일(build/ai-eval/, 커밋하지 않음)에만 남긴다.
 */
public final class PolicyRagEvalReport {

    private PolicyRagEvalReport() {
    }

    /**
     * @param title   리포트 제목 (2단계 a / b)
     * @param context 머리말에 넣을 실행 정보
     */
    public static void write(Path dir, String title, List<Result> results, Map<String, Object> context) throws IOException {
        Files.createDirectories(dir);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("context", context);
        json.put("results", results.stream().map(PolicyRagEvalReport::json).toList());
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(dir.resolve("results.json").toFile(), json);
        Files.writeString(dir.resolve("report.md"), markdown(title, results, context), StandardCharsets.UTF_8);
    }

    static Map<String, Object> json(Result r) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("caseId", r.evalCase().id());
        json.put("category", r.evalCase().category());
        json.put("promptVersion", r.promptVersion());
        json.put("model", r.model());
        json.put("question", r.evalCase().question());
        json.put("hits", r.hitKeys());
        json.put("searchMissed", r.searchMissed());
        json.put("expectedStatus", r.evalCase().expectedStatus());
        json.put("status", r.status());
        json.put("sources", r.sources());
        json.put("answer", r.answer());
        json.put("rawAnswer", r.rawAnswer());
        json.put("passed", r.score().passed());
        json.put("failures", r.score().failures());
        json.put("citationMissing", r.score().citationMissing());
        json.put("invalidCitations", r.score().invalidCitations());
        json.put("inputTokens", r.inputTokens());
        json.put("outputTokens", r.outputTokens());
        json.put("reasoningTokens", r.reasoningTokens());
        json.put("elapsedMs", r.elapsedMs());
        json.put("error", r.error());
        return json;
    }

    static String markdown(String title, List<Result> results, Map<String, Object> context) {
        StringBuilder md = new StringBuilder("# ").append(title).append("\n\n");
        context.forEach((key, value) -> md.append("- ").append(key).append(": ").append(value).append('\n'));
        md.append("""

                자동 채점은 문자열·근거 비교만 하는 1차 검사다. 문항마다 답변을 읽고 "사람 검수" 항목을 확인한다
                (원문에 없는 내용, 자격 단정, 다른 정책 내용 섞임). LLM 채점은 쓰지 않는다.
                """);

        Map<String, List<Result>> byPrompt = results.stream()
                .collect(Collectors.groupingBy(Result::promptVersion, TreeMap::new, Collectors.toList()));
        md.append("\n## 요약\n\n| 프롬프트 | 통과 | 상태 일치 | 근거 정확 | 금지 표현 | 관련 없음 → NO_EVIDENCE | 근거 번호 누락 | 무효 번호 | 근거 범위 밖 | 호출 실패 | 평균 입력/출력 토큰 | 평균·최대 응답(ms) |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        byPrompt.forEach((prompt, rows) -> {
            Summary s = PolicyRagScorer.summarize(rows.stream().map(Result::score).toList());
            List<Result> called = rows.stream().filter(r -> r.rawAnswer() != null).toList();
            md.append(String.format(Locale.ROOT, "| %s | %d/%d | %d/%d | %d/%d | %d | %d/%d | %d | %d | %d | %d | %.0f / %.0f | %.0f / %d |%n",
                    prompt, s.passed(), s.cases(), s.statusMatched(), s.cases(), s.sourcesCorrect(), s.sourceTargets(),
                    s.fabricated(), s.noEvidenceCorrect(), s.noRelevantCases(), s.citationMissing(), s.invalidCitations(),
                    s.outOfRangeCases(), rows.stream().filter(r -> r.error() != null).count(),
                    called.stream().mapToInt(Result::inputTokens).average().orElse(0),
                    called.stream().mapToInt(Result::outputTokens).average().orElse(0),
                    called.stream().mapToLong(Result::elapsedMs).average().orElse(0),
                    called.stream().mapToLong(Result::elapsedMs).max().orElse(0)));
        });

        md.append("\n## 유형별 통과\n\n| 유형 | ").append(String.join(" | ", byPrompt.keySet())).append(" |\n|---|")
                .append("---|".repeat(byPrompt.size())).append('\n');
        for (Category category : Category.values()) {
            if (results.stream().noneMatch(r -> r.evalCase().category() == category)) {
                continue;
            }
            md.append("| ").append(category).append(" | ").append(byPrompt.values().stream().map(rows -> {
                List<Result> ofCategory = rows.stream().filter(r -> r.evalCase().category() == category).toList();
                return ofCategory.stream().filter(r -> r.score().passed()).count() + "/" + ofCategory.size();
            }).collect(Collectors.joining(" | "))).append(" |\n");
        }

        md.append("\n## 문항별 결과\n");
        Map<String, List<Result>> byCase = results.stream()
                .collect(Collectors.groupingBy(r -> r.evalCase().id(), LinkedHashMap::new, Collectors.toList()));
        byCase.values().forEach(rows -> {
            RagCase c = rows.getFirst().evalCase();
            Result first = rows.getFirst();
            md.append("\n### ").append(c.id()).append(" (").append(c.category()).append(")\n\n")
                    .append("- 질문: ").append(c.question()).append('\n')
                    .append("- 사용자 정보: ").append(c.profile()).append('\n')
                    .append("- 기대 상태: ").append(c.expectedStatus())
                    .append(c.expectedSources().isEmpty() ? "" : ", 기대 근거 " + c.expectedSources()).append('\n');
            if (c.note() != null) {
                md.append("- 기대: ").append(c.note()).append('\n');
            }
            md.append("- 검색 결과: ").append(first.hitKeys().isEmpty() ? "(없음)" : numbered(first.hitKeys(), first.hitNames()))
                    .append('\n');
            if (!first.searchMissed().isEmpty()) {
                md.append("- 검색 누락 (기대 근거가 검색 결과에 없음): ").append(first.searchMissed()).append('\n');
            }
            for (Result r : rows) {
                md.append("\n**").append(r.promptVersion()).append("** ").append(r.score().passed() ? "통과" : "실패")
                        .append(" · ").append(r.status() == null ? "호출 실패" : r.status())
                        .append(r.sources().isEmpty() ? "" : " · 근거 " + r.sources());
                if (r.rawAnswer() != null) {
                    md.append(" · 출력 ").append(r.outputTokens()).append(" 토큰 · ").append(r.elapsedMs()).append("ms");
                }
                md.append('\n');
                r.score().failures().forEach(f -> md.append("- 실패: ").append(f).append('\n'));
                if (r.error() != null) {
                    md.append("- 오류: ").append(r.error()).append('\n');
                }
                if (r.rawAnswer() != null) {
                    md.append("\n원문 답변:\n\n> ").append(r.rawAnswer().strip().replace("\n", "\n> ")).append('\n');
                    if (r.status() != null && r.answer() != null) {
                        md.append("\n서버 답변 (").append(r.status()).append("):\n\n> ")
                                .append(r.answer().strip().replace("\n", "\n> ")).append('\n');
                    }
                    md.append("\n- [ ] 사람 검수: 원문에 없는 내용·자격 단정·다른 정책 내용 섞임 없음\n");
                }
            }
        });
        return md.toString();
    }

    private static String numbered(List<String> keys, List<String> names) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            text.append(i == 0 ? "" : ", ").append('[').append(i + 1).append("] ")
                    .append(i < names.size() ? names.get(i) : "").append(" (`").append(keys.get(i)).append("`)");
        }
        return text.toString();
    }
}
