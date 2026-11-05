package kr.fintarget.api.domain.policy.rag.eval;

import kr.fintarget.api.domain.ai.chat.eval.PolicyQaScorer;
import kr.fintarget.api.domain.policy.rag.PolicyCitationParser;
import kr.fintarget.api.domain.policy.rag.PolicyRagStatus;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalSet.Category;
import kr.fintarget.api.domain.policy.rag.eval.PolicyRagEvalSet.RagCase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 통합 정책 AI 답변 자동 채점 (#184 2단계). 문자열 포함 여부와 근거 정책 비교만 보는 1차 검사이며,
 * 근거 없는 내용(지어냄) 판정은 사람이 리포트를 보고 확정한다. LLM 채점은 쓰지 않는다.
 *
 * <ul>
 *   <li>상태 일치: 결과 상태가 기대 상태 목록 안에 있는지</li>
 *   <li>근거 범위: 근거 정책이 모두 검색 결과 안에 있는지 (구조상 항상 참이어야 하는 회귀 확인)</li>
 *   <li>근거 정확도: ANSWERED일 때 기대 근거를 모두 인용하고 금지 근거를 인용하지 않았는지</li>
 *   <li>원문 사실: ANSWERED일 때 expectedFacts 그룹마다 대안 하나 이상 (공백·쉼표 무시, #126과 같은 정규화)</li>
 *   <li>근거 없는 내용: mustNotInclude, 프롬프트 자리표시자 노출, 자격 단정 표현</li>
 *   <li>근거 번호 누락: 검색 결과가 있는데 AI 답변이 NO_EVIDENCE로 바뀜 (유효한 근거 번호 없음). 무효 번호 수도 센다</li>
 * </ul>
 */
public final class PolicyRagScorer {

    static final List<String> LEAK_MARKERS = List.of("{{chunks}}", "{{profile}}");

    private PolicyRagScorer() {
    }

    /**
     * @param hits 문항 검색 결과 키 (순서 = 근거 번호)
     */
    public static Score score(RagCase c, List<String> hits, Outcome outcome) {
        List<String> failures = new ArrayList<>();
        String normalized = PolicyQaScorer.normalize(outcome.answer() == null ? "" : outcome.answer());
        boolean answered = outcome.status() == PolicyRagStatus.ANSWERED;

        boolean statusMatched = c.expectedStatus().contains(outcome.status());
        if (!statusMatched) {
            failures.add("상태 불일치: " + outcome.status() + " (기대 " + c.expectedStatus() + ")");
        }
        List<String> outOfRange = outcome.sources().stream().filter(source -> !hits.contains(source)).toList();
        if (!outOfRange.isEmpty()) {
            failures.add("근거가 검색 결과 밖: " + outOfRange);
        }

        List<String> missingSources = List.of();
        List<String> forbiddenCited = outcome.sources().stream().filter(c.forbiddenSources()::contains).toList();
        if (answered) {
            missingSources = c.expectedSources().stream().filter(source -> !outcome.sources().contains(source)).toList();
            if (!missingSources.isEmpty()) {
                failures.add("기대 근거 누락: " + missingSources);
            }
            for (List<String> group : c.expectedFacts()) {
                if (group.stream().map(PolicyQaScorer::normalize).noneMatch(normalized::contains)) {
                    failures.add("원문 사실 누락: " + String.join(" | ", group));
                }
            }
            if (c.notInSource() && PolicyQaScorer.NOT_IN_SOURCE_MARKERS.stream().noneMatch(normalized::contains)) {
                failures.add("원문 확인 불가 안내 없음");
            }
            if (c.needsCheck() && PolicyQaScorer.NEEDS_CHECK_MARKERS.stream().noneMatch(normalized::contains)) {
                failures.add("확인 필요 안내 없음");
            }
        }
        if (!forbiddenCited.isEmpty()) {
            failures.add("금지 근거 인용: " + forbiddenCited);
        }

        List<String> fabricated = c.mustNotInclude().stream()
                .filter(phrase -> normalized.contains(PolicyQaScorer.normalize(phrase))).toList();
        fabricated.forEach(phrase -> failures.add("금지 표현 포함: " + phrase));
        String answerText = outcome.answer() == null ? "" : outcome.answer();
        LEAK_MARKERS.stream().filter(answerText::contains).forEach(leak -> failures.add("프롬프트 자리표시자 노출: " + leak));
        if (c.noDefinitiveEligibility()) {
            PolicyQaScorer.DEFINITIVE_MARKERS.stream().filter(normalized::contains).findFirst()
                    .ifPresent(marker -> failures.add("자격 단정 의심: " + marker));
        }

        boolean aiCalled = outcome.rawAnswer() != null;
        boolean citationMissing = aiCalled && !hits.isEmpty() && outcome.status() == PolicyRagStatus.NO_EVIDENCE;
        int invalidCitations = aiCalled ? PolicyCitationParser.parse(outcome.rawAnswer(), hits.size()).invalidCount() : 0;
        if (citationMissing && !statusMatched) {
            failures.add("근거 번호 누락으로 NO_EVIDENCE 대체");
        }
        return new Score(c, failures.isEmpty(), List.copyOf(failures), statusMatched, outOfRange.isEmpty(),
                answered && missingSources.isEmpty() && forbiddenCited.isEmpty(), fabricated.size(), citationMissing,
                invalidCitations);
    }

    public static Summary summarize(List<Score> scores) {
        Map<Category, int[]> byCategory = new LinkedHashMap<>();
        for (Category category : Category.values()) {
            List<Score> ofCategory = scores.stream().filter(s -> s.evalCase().category() == category).toList();
            if (!ofCategory.isEmpty()) {
                byCategory.put(category, new int[]{(int) ofCategory.stream().filter(Score::passed).count(), ofCategory.size()});
            }
        }
        // 근거 정확도는 ANSWERED만 기대하는 문항(기대 근거가 정해진 문항) 기준
        List<Score> sourceTargets = scores.stream()
                .filter(s -> s.evalCase().expectedStatus().equals(List.of(PolicyRagStatus.ANSWERED))).toList();
        List<Score> noRelevant = scores.stream().filter(s -> s.evalCase().category() == Category.NO_RELEVANT).toList();
        return new Summary(scores.size(), (int) scores.stream().filter(Score::passed).count(),
                (int) scores.stream().filter(Score::statusMatched).count(),
                (int) sourceTargets.stream().filter(Score::sourcesCorrect).count(), sourceTargets.size(),
                scores.stream().mapToInt(Score::fabricatedCount).sum(),
                (int) noRelevant.stream().filter(Score::statusMatched).count(), noRelevant.size(),
                (int) scores.stream().filter(s -> s.citationMissing() && !s.statusMatched()).count(),
                scores.stream().mapToInt(Score::invalidCitations).sum(),
                (int) scores.stream().filter(s -> !s.sourcesInRange()).count(),
                byCategory);
    }

    /**
     * 답변 1건의 결과.
     *
     * @param sources   근거 정책 키 (인용 순서)
     * @param rawAnswer 모델 원문 답변 (근거 번호 포함). AI를 호출하지 않았으면 null
     */
    public record Outcome(PolicyRagStatus status, String answer, List<String> sources, String rawAnswer) {

        public Outcome {
            sources = List.copyOf(sources);
        }
    }

    /**
     * @param sourcesCorrect   ANSWERED이고 기대 근거를 모두 인용했으며 금지 근거가 없음
     * @param fabricatedCount  금지 표현(지어낸 값 등) 건수
     * @param citationMissing  검색 결과가 있는데 유효한 근거 번호가 없어 NO_EVIDENCE가 됨 (NO_RELEVANT 문항에서는 기대 동작)
     * @param invalidCitations 검색 결과에 없는 번호 수 (서버가 버림)
     */
    public record Score(RagCase evalCase, boolean passed, List<String> failures, boolean statusMatched,
                        boolean sourcesInRange, boolean sourcesCorrect, int fabricatedCount, boolean citationMissing,
                        int invalidCitations) {
    }

    /**
     * @param sourcesCorrect     근거 정확도 분자 (ANSWERED만 기대하는 문항 중 근거가 맞은 수)
     * @param sourceTargets      근거 정확도 분모
     * @param noEvidenceCorrect  NO_RELEVANT 문항 중 NO_EVIDENCE로 답한 수 (모른다고 답하는 비율 분자)
     * @param citationMissing    ANSWERED를 기대했는데 근거 번호 누락으로 NO_EVIDENCE가 된 수
     * @param outOfRangeCases    근거가 검색 결과 밖인 문항 수 (0이어야 함)
     * @param byCategory         유형 → [통과, 전체]
     */
    public record Summary(int cases, int passed, int statusMatched, int sourcesCorrect, int sourceTargets,
                          int fabricated, int noEvidenceCorrect, int noRelevantCases, int citationMissing,
                          int invalidCitations, int outOfRangeCases, Map<Category, int[]> byCategory) {
    }
}
