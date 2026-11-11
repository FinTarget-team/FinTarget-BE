package kr.fintarget.api.domain.ai.chat.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.fintarget.api.common.config.OpenAiConfig;
import kr.fintarget.api.domain.ai.chat.eval.PolicyQaEvalSet.EvalCase;
import kr.fintarget.api.domain.ai.chat.eval.PolicyQaScorer.Score;
import kr.fintarget.api.domain.ai.chat.service.PolicyDocument;
import kr.fintarget.api.domain.ai.chat.service.PolicyQaPrompt;
import kr.fintarget.api.domain.ai.client.AiModelTier;
import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.ai.client.AiTextRequest;
import kr.fintarget.api.domain.ai.client.AiTextResult;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 평가셋(#126)으로 실제 OpenAI를 호출해 모델·프롬프트 버전별 답변을 모으고 자동 채점한다.
 * 비용이 들므로 OPENAI_API_KEY와 RUN_AI_EVAL=true가 모두 있을 때만 실행된다 (일반 ./gradlew test에서는 skip).
 *
 * <p>운영과 같은 빈 구성(OpenAiConfig, OpenAiHttpExecutor, OpenAiResponsesClient)과 코드 기본 설정을 쓴다.
 * 결과는 build/ai-eval/{실행 시각}/에 results.json, report.md로 남긴다 (build/는 .gitignore 대상).
 *
 * <p>환경 변수: AI_EVAL_TIERS(기본 DEFAULT,PREMIUM), AI_EVAL_PROMPTS(기본 현재 VERSION),
 * AI_EVAL_CASES(문항 id 쉼표 구분, 비우면 전체), AI_EVAL_PARALLELISM(기본 4)
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "RUN_AI_EVAL", matches = "true")
class PolicyQaEvalLiveRun {

    private static final Path OUTPUT_ROOT = Path.of("build", "ai-eval");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(EvalAiConfig.class)
            .withPropertyValues("openai.api-key=" + System.getenv("OPENAI_API_KEY"));

    @Test
    void 평가셋_전체를_모델과_프롬프트_버전별로_실행하고_리포트를_남긴다() {
        PolicyQaEvalSet evalSet = PolicyQaEvalSet.load();
        List<AiModelTier> tiers = csv("AI_EVAL_TIERS", "DEFAULT,PREMIUM").stream().map(AiModelTier::valueOf).toList();
        List<PolicyQaPrompt> prompts = csv("AI_EVAL_PROMPTS", PolicyQaPrompt.VERSION).stream()
                .map(PolicyQaPrompt::ofVersion).toList();
        Set<String> onlyCases = Set.copyOf(csv("AI_EVAL_CASES", ""));
        List<EvalCase> cases = evalSet.cases().stream()
                .filter(c -> onlyCases.isEmpty() || onlyCases.contains(c.id())).toList();
        int parallelism = Integer.parseInt(env("AI_EVAL_PARALLELISM", "4"));

        contextRunner.run(context -> {
            AiTextClient client = context.getBean(AiTextClient.class);
            List<Future<Result>> futures = new ArrayList<>();
            try (ExecutorService executor = Executors.newFixedThreadPool(parallelism)) {
                for (PolicyQaPrompt prompt : prompts) {
                    for (AiModelTier tier : tiers) {
                        for (EvalCase evalCase : cases) {
                            futures.add(executor.submit(() -> run(client, evalSet, evalCase, prompt, tier)));
                        }
                    }
                }
            }
            List<Result> results = new ArrayList<>();
            for (Future<Result> future : futures) {
                results.add(future.get());
            }
            results.sort(Comparator.comparing(Result::promptVersion).thenComparing(Result::tier)
                    .thenComparing(r -> cases.indexOf(r.evalCase())));

            Path dir = write(evalSet, results);
            log.info("평가 완료 {}건, 결과: {}", results.size(), dir.toAbsolutePath());
            assertThat(results).hasSize(prompts.size() * tiers.size() * cases.size());
        });
    }

    private static Result run(AiTextClient client, PolicyQaEvalSet evalSet, EvalCase evalCase, PolicyQaPrompt prompt,
                              AiModelTier tier) {
        PolicyDocument document = evalSet.documentOf(evalCase);
        AiTextRequest request = new AiTextRequest(
                prompt.render(document, evalSet.profileOf(evalCase)), evalCase.messages(), tier);
        Instant startedAt = Instant.now();
        try {
            AiTextResult result = client.generate(request);
            long elapsedMs = Duration.between(startedAt, Instant.now()).toMillis();
            String answer = result.text().strip();
            return new Result(evalCase, prompt.version(), tier, result.model(), answer, document.sourceLimited(),
                    PolicyQaScorer.score(evalCase, answer), result.inputTokens(), result.outputTokens(),
                    result.reasoningTokens(), elapsedMs, null);
        } catch (RuntimeException e) {
            long elapsedMs = Duration.between(startedAt, Instant.now()).toMillis();
            return new Result(evalCase, prompt.version(), tier, null, null, document.sourceLimited(),
                    new Score(false, List.of("호출 실패: " + e.getClass().getSimpleName()), List.of(), 0),
                    0, 0, 0, elapsedMs, e.getMessage());
        }
    }

    private static Path write(PolicyQaEvalSet evalSet, List<Result> results) throws IOException {
        Path dir = OUTPUT_ROOT.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        Files.createDirectories(dir);
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(dir.resolve("results.json").toFile(), results.stream().map(Result::toJson).toList());
        Files.writeString(dir.resolve("report.md"), report(evalSet, results), StandardCharsets.UTF_8);
        return dir;
    }

    private static String report(PolicyQaEvalSet evalSet, List<Result> results) {
        StringBuilder md = new StringBuilder("# 정책 AI 질문 평가 리포트\n\n");
        md.append("- 평가셋: `").append(PolicyQaEvalSet.RESOURCE).append("` (").append(evalSet.cases().size())
                .append("문항)\n- 실행: ").append(LocalDateTime.now()).append("\n\n");

        md.append("## 요약\n\n| 프롬프트 | 모델 | 통과 | 평균 입력 토큰 | 평균 출력 토큰 | 평균 추론 토큰 | 평균 응답(ms) | 최대 응답(ms) |\n")
                .append("|---|---|---|---|---|---|---|---|\n");
        group(results, r -> r.promptVersion() + " / " + r.model()).forEach((key, rows) -> md.append(String.format(
                "| %s | %s | %d/%d | %.0f | %.0f | %.0f | %.0f | %d |%n", rows.getFirst().promptVersion(),
                rows.getFirst().model(), rows.stream().filter(r -> r.score().passed()).count(), rows.size(),
                rows.stream().mapToInt(Result::inputTokens).average().orElse(0),
                rows.stream().mapToInt(Result::outputTokens).average().orElse(0),
                rows.stream().mapToInt(Result::reasoningTokens).average().orElse(0),
                rows.stream().mapToLong(Result::elapsedMs).average().orElse(0),
                rows.stream().mapToLong(Result::elapsedMs).max().orElse(0))));

        md.append("\n## 유형별 통과\n\n| 유형 | ")
                .append(group(results, Result::column).keySet().stream().collect(Collectors.joining(" | ")))
                .append(" |\n|---|").append("---|".repeat(group(results, Result::column).size())).append('\n');
        group(results, r -> r.evalCase().category().name()).forEach((category, rows) -> {
            md.append("| ").append(category).append(" | ");
            md.append(group(rows, Result::column).values().stream()
                    .map(cell -> cell.stream().filter(r -> r.score().passed()).count() + "/" + cell.size())
                    .collect(Collectors.joining(" | ")));
            md.append(" |\n");
        });

        md.append("\n## 문항별 결과\n");
        group(results, r -> r.evalCase().id()).values().stream()
                .sorted(Comparator.comparing(rows -> evalSet.cases().indexOf(rows.getFirst().evalCase())))
                .forEach(rows -> {
                    EvalCase c = rows.getFirst().evalCase();
                    md.append("\n### ").append(c.id()).append(" (").append(c.category()).append(" → ").append(c.behavior())
                            .append(")\n\n- 정책: ").append(evalSet.policies().get(c.policy()).name())
                            .append(rows.getFirst().sourceLimited() ? " (원문 부족)" : "")
                            .append("\n- 사용자 정보: ").append(c.profile()).append("\n");
                    c.history().forEach(t -> md.append("- 이전 ").append(t.role()).append(": ").append(t.content()).append('\n'));
                    md.append("- 질문: ").append(c.question()).append('\n');
                    if (c.note() != null) {
                        md.append("- 기대: ").append(c.note()).append('\n');
                    }
                    for (Result r : rows) {
                        md.append("\n**").append(r.column()).append("** ").append(r.score().passed() ? "통과" : "실패")
                                .append(" · 출력 ").append(r.outputTokens()).append(" 토큰 · ").append(r.elapsedMs()).append("ms\n");
                        r.score().failures().forEach(f -> md.append("- 실패: ").append(f).append('\n'));
                        r.score().warnings().forEach(w -> md.append("- 경고: ").append(w).append('\n'));
                        md.append("\n> ").append(r.answer() == null ? "(응답 없음) " + r.error() : r.answer().replace("\n", "\n> "))
                                .append('\n');
                    }
                });
        return md.toString();
    }

    private static Map<String, List<Result>> group(List<Result> results, Function<Result, String> key) {
        return results.stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.toList()));
    }

    private static List<String> csv(String name, String defaultValue) {
        return Arrays.stream(env(name, defaultValue).split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    record Result(EvalCase evalCase, String promptVersion, AiModelTier tier, String model, String answer,
                  boolean sourceLimited, Score score, int inputTokens, int outputTokens, int reasoningTokens,
                  long elapsedMs, String error) {

        String column() {
            return promptVersion + " / " + tier;
        }

        Map<String, Object> toJson() {
            Map<String, Object> json = new TreeMap<>();
            json.put("caseId", evalCase.id());
            json.put("category", evalCase.category());
            json.put("behavior", evalCase.behavior());
            json.put("promptVersion", promptVersion);
            json.put("tier", tier);
            json.put("model", model);
            json.put("question", evalCase.question());
            json.put("answer", answer);
            json.put("sourceLimited", sourceLimited);
            json.put("passed", score.passed());
            json.put("failures", score.failures());
            json.put("warnings", score.warnings());
            json.put("sentenceCount", score.sentenceCount());
            json.put("inputTokens", inputTokens);
            json.put("outputTokens", outputTokens);
            json.put("reasoningTokens", reasoningTokens);
            json.put("elapsedMs", elapsedMs);
            json.put("error", error);
            return json;
        }
    }

    // 운영과 같은 AI 클라이언트 빈 구성. OpenAiHttpExecutor가 패키지 전용이라 클래스 대신 패키지를 스캔한다
    @Configuration
    @Import(OpenAiConfig.class)
    @ComponentScan(basePackageClasses = AiTextClient.class)
    static class EvalAiConfig {
    }
}
