package kr.fintarget.api.domain.policy.benefit.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.fintarget.api.common.config.OpenAiConfig;
import kr.fintarget.api.domain.ai.client.AiMessage;
import kr.fintarget.api.domain.ai.client.AiModelTier;
import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.ai.client.AiTextRequest;
import kr.fintarget.api.domain.ai.client.AiTextResult;
import kr.fintarget.api.domain.policy.benefit.BenefitExtraction;
import kr.fintarget.api.domain.policy.benefit.BenefitExtractionParser;
import kr.fintarget.api.domain.policy.benefit.BenefitSource;
import kr.fintarget.api.domain.policy.benefit.PolicyBenefitPrompt;
import kr.fintarget.api.domain.policy.benefit.eval.PolicyBenefitEvalSet.EvalCase;
import kr.fintarget.api.domain.policy.benefit.eval.PolicyBenefitScorer.Score;
import kr.fintarget.api.domain.policy.benefit.eval.PolicyBenefitScorer.Verdict;
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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 혜택 추출 평가셋(#162)으로 실제 OpenAI를 호출해 모델·프롬프트 버전별 추출 결과를 채점한다.
 * 비용이 들므로 OPENAI_API_KEY와 RUN_AI_EVAL=true가 모두 있을 때만 실행된다 (일반 ./gradlew test에서는 skip).
 * 결과는 build/ai-eval/benefit-{실행 시각}/에 results.json, report.md로 남긴다.
 *
 * <p>환경 변수: AI_EVAL_TIERS(기본 DEFAULT), AI_EVAL_PROMPTS(기본 현재 VERSION),
 * AI_EVAL_CASES(문항 id 쉼표 구분, 비우면 전체), AI_EVAL_PARALLELISM(기본 4)
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "RUN_AI_EVAL", matches = "true")
class PolicyBenefitEvalLiveRun {

    private static final Path OUTPUT_ROOT = Path.of("build", "ai-eval");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(EvalAiConfig.class)
            .withPropertyValues("openai.api-key=" + System.getenv("OPENAI_API_KEY"));

    @Test
    void 평가셋_전체를_추출하고_채점_리포트를_남긴다() {
        PolicyBenefitEvalSet evalSet = PolicyBenefitEvalSet.load();
        List<AiModelTier> tiers = csv("AI_EVAL_TIERS", "DEFAULT").stream().map(AiModelTier::valueOf).toList();
        List<PolicyBenefitPrompt> prompts = csv("AI_EVAL_PROMPTS", PolicyBenefitPrompt.VERSION).stream()
                .map(PolicyBenefitPrompt::ofVersion).toList();
        Set<String> onlyCases = Set.copyOf(csv("AI_EVAL_CASES", ""));
        List<EvalCase> cases = evalSet.cases().stream()
                .filter(c -> onlyCases.isEmpty() || onlyCases.contains(c.id())).toList();
        int parallelism = Integer.parseInt(env("AI_EVAL_PARALLELISM", "4"));

        contextRunner.run(context -> {
            AiTextClient client = context.getBean(AiTextClient.class);
            List<Future<Result>> futures = new ArrayList<>();
            try (ExecutorService executor = Executors.newFixedThreadPool(parallelism)) {
                for (PolicyBenefitPrompt prompt : prompts) {
                    for (AiModelTier tier : tiers) {
                        for (EvalCase evalCase : cases) {
                            futures.add(executor.submit(() -> run(client, evalCase, prompt, tier)));
                        }
                    }
                }
            }
            List<Result> results = new ArrayList<>();
            for (Future<Result> future : futures) {
                results.add(future.get());
            }
            results.sort(Comparator.comparing(Result::column).thenComparing(r -> cases.indexOf(r.evalCase())));

            Path dir = write(evalSet, results);
            log.info("평가 완료 {}건, 결과: {}", results.size(), dir.toAbsolutePath());
            assertThat(results).hasSize(prompts.size() * tiers.size() * cases.size());
        });
    }

    private static Result run(AiTextClient client, EvalCase evalCase, PolicyBenefitPrompt prompt, AiModelTier tier) {
        BenefitSource source = evalCase.policy().toSource();
        AiTextRequest request = new AiTextRequest(prompt.instructions(),
                List.of(AiMessage.user(prompt.userMessage(source))), tier);
        Instant startedAt = Instant.now();
        String raw = null;
        String model = null;
        int inputTokens = 0;
        int outputTokens = 0;
        try {
            AiTextResult result = client.generate(request);
            raw = result.text();
            model = result.model();
            inputTokens = result.inputTokens();
            outputTokens = result.outputTokens();
            BenefitExtraction extraction = BenefitExtractionParser.parse(raw, source);
            return new Result(evalCase, prompt.version(), tier, model, raw, extraction,
                    PolicyBenefitScorer.score(evalCase.expected(), extraction), inputTokens, outputTokens,
                    elapsed(startedAt), null);
        } catch (RuntimeException e) {
            // 호출·해석 실패는 운영에서 "추출 건너뜀"이므로 MISSED와 같은 영향(반영 안 됨)으로 본다
            Verdict verdict = evalCase.expected().type().reflectsInSimulation() ? Verdict.MISSED : Verdict.CORRECT;
            return new Result(evalCase, prompt.version(), tier, model, raw, null,
                    new Score(verdict, List.of("실패: " + e.getClass().getSimpleName() + " " + e.getMessage()), List.of()),
                    inputTokens, outputTokens, elapsed(startedAt), e.getMessage());
        }
    }

    private static long elapsed(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }

    private static Path write(PolicyBenefitEvalSet evalSet, List<Result> results) throws IOException {
        Path dir = OUTPUT_ROOT.resolve("benefit-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        Files.createDirectories(dir);
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(dir.resolve("results.json").toFile(), results.stream().map(Result::toJson).toList());
        Files.writeString(dir.resolve("report.md"), report(evalSet, results), StandardCharsets.UTF_8);
        return dir;
    }

    private static String report(PolicyBenefitEvalSet evalSet, List<Result> results) {
        StringBuilder md = new StringBuilder("# 정책 혜택 추출 평가 리포트\n\n");
        md.append("- 평가셋: `").append(PolicyBenefitEvalSet.RESOURCE).append("` (").append(evalSet.cases().size())
                .append("건)\n- 실행: ").append(LocalDateTime.now()).append("\n\n");

        md.append("## 요약\n\n| 프롬프트 / 모델 | 정답 | 잘못 반영 | 놓침 | 금액 오류 | 유형만 다름 | 실패 | 평균 입력 토큰 | 평균 출력 토큰 | 평균 응답(ms) |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|\n");
        Map<String, List<Result>> byColumn = results.stream()
                .collect(Collectors.groupingBy(Result::column, TreeMap::new, Collectors.toList()));
        byColumn.forEach((column, rows) -> {
            Map<Verdict, Long> counts = new EnumMap<>(Verdict.class);
            rows.forEach(r -> counts.merge(r.score().verdict(), 1L, Long::sum));
            md.append(String.format("| %s (%s) | %d | %d | %d | %d | %d | %d | %.0f | %.0f | %.0f |%n", column,
                    rows.getFirst().model(), counts.getOrDefault(Verdict.CORRECT, 0L),
                    counts.getOrDefault(Verdict.FALSE_REFLECT, 0L), counts.getOrDefault(Verdict.MISSED, 0L),
                    counts.getOrDefault(Verdict.WRONG_AMOUNT, 0L), counts.getOrDefault(Verdict.WRONG_TYPE, 0L),
                    rows.stream().filter(r -> r.error() != null).count(),
                    rows.stream().mapToInt(Result::inputTokens).average().orElse(0),
                    rows.stream().mapToInt(Result::outputTokens).average().orElse(0),
                    rows.stream().mapToLong(Result::elapsedMs).average().orElse(0)));
        });

        md.append("\n## 틀린 문항\n");
        for (Result r : results) {
            if (r.score().verdict() == Verdict.CORRECT && r.score().warnings().isEmpty()) {
                continue;
            }
            EvalCase c = r.evalCase();
            md.append("\n### ").append(c.policy().name()).append(" (").append(c.id()).append(") — ")
                    .append(r.column()).append(" ").append(r.score().verdict()).append("\n\n");
            md.append("- 정답: ").append(c.expected().type()).append(" monthly=").append(c.expected().monthlyAmount())
                    .append(" months=").append(c.expected().months()).append(" oneTime=").append(c.expected().oneTimeAmount())
                    .append('\n');
            if (c.reviewNote() != null) {
                md.append("- 정답 메모: ").append(c.reviewNote()).append('\n');
            }
            r.score().details().forEach(d -> md.append("- 차이: ").append(d).append('\n'));
            r.score().warnings().forEach(w -> md.append("- 경고: ").append(w).append('\n'));
            md.append("- 지원 내용: ").append(oneLine(c.policy().supportContent())).append('\n');
            md.append("- 응답: `").append(oneLine(r.raw())).append("`\n");
        }
        return md.toString();
    }

    private static String oneLine(String text) {
        return text == null ? "(없음)" : text.replaceAll("\\s+", " ").strip();
    }

    private static List<String> csv(String name, String defaultValue) {
        return Arrays.stream(env(name, defaultValue).split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    record Result(EvalCase evalCase, String promptVersion, AiModelTier tier, String model, String raw,
                  BenefitExtraction extraction, Score score, int inputTokens, int outputTokens, long elapsedMs,
                  String error) {

        String column() {
            return promptVersion + " / " + tier;
        }

        Map<String, Object> toJson() {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("caseId", evalCase.id());
            json.put("name", evalCase.policy().name());
            json.put("promptVersion", promptVersion);
            json.put("tier", tier);
            json.put("model", model);
            json.put("expected", evalCase.expected());
            json.put("extraction", extraction);
            json.put("raw", raw);
            json.put("verdict", score.verdict());
            json.put("details", score.details());
            json.put("warnings", score.warnings());
            json.put("inputTokens", inputTokens);
            json.put("outputTokens", outputTokens);
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
