package kr.fintarget.api.domain.ai.chat.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.fintarget.api.domain.ai.chat.eval.PolicyQaEvalSet.EvalCase;
import kr.fintarget.api.domain.ai.chat.eval.PolicyQaScorer.Score;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 이전 평가 실행(results.json)의 답변을 현재 평가셋·채점기로 다시 채점한다. OpenAI를 호출하지 않는다.
 * 기대값이나 채점 규칙을 고친 뒤 같은 답변으로 자동 판정이 어떻게 바뀌는지 확인할 때 쓴다.
 *
 * <p>AI_EVAL_RESCORE에 실행 디렉터리(예: build/ai-eval/20261030-134349)를 주면 실행되고, 같은 디렉터리에 rescore.md를 남긴다.
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "AI_EVAL_RESCORE", matches = ".+")
class PolicyQaEvalRescore {

    @Test
    void 저장된_답변을_현재_평가셋으로_다시_채점한다() throws IOException {
        Path dir = Path.of(System.getenv("AI_EVAL_RESCORE"));
        List<Map<String, Object>> rows = new ObjectMapper().readValue(
                dir.resolve("results.json").toFile(), new TypeReference<>() {
                });
        Map<String, EvalCase> cases = PolicyQaEvalSet.load().cases().stream()
                .collect(Collectors.toMap(EvalCase::id, Function.identity()));

        StringBuilder md = new StringBuilder("# 재채점 결과\n\n- 원본: `").append(dir).append("/results.json`\n\n")
                .append("| 프롬프트 / 등급 | 원래 통과 | 재채점 통과 |\n|---|---|---|\n");
        Map<String, int[]> summary = new TreeMap<>();
        StringBuilder details = new StringBuilder("\n## 재채점 후 실패\n\n");
        for (Map<String, Object> row : rows) {
            EvalCase evalCase = cases.get((String) row.get("caseId"));
            String answer = (String) row.get("answer");
            if (evalCase == null || answer == null) {
                continue;
            }
            Score score = PolicyQaScorer.score(evalCase, answer);
            int[] counts = summary.computeIfAbsent(row.get("promptVersion") + " / " + row.get("tier"), k -> new int[3]);
            counts[0] += Boolean.TRUE.equals(row.get("passed")) ? 1 : 0;
            counts[1] += score.passed() ? 1 : 0;
            counts[2]++;
            if (!score.passed()) {
                details.append("- ").append(evalCase.id()).append(" (").append(row.get("tier")).append("): ")
                        .append(String.join(", ", score.failures())).append('\n');
            }
        }
        summary.forEach((key, c) -> md.append("| ").append(key).append(" | ").append(c[0]).append('/').append(c[2])
                .append(" | ").append(c[1]).append('/').append(c[2]).append(" |\n"));
        Files.writeString(dir.resolve("rescore.md"), md.append(details), StandardCharsets.UTF_8);
        log.info("재채점 완료: {}", dir.resolve("rescore.md").toAbsolutePath());
    }
}
