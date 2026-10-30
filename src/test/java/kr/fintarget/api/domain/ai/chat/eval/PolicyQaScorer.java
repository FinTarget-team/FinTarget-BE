package kr.fintarget.api.domain.ai.chat.eval;

import kr.fintarget.api.domain.ai.chat.eval.PolicyQaEvalSet.Behavior;
import kr.fintarget.api.domain.ai.chat.eval.PolicyQaEvalSet.EvalCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 평가 답변 자동 채점 (#126). 문자열 포함 여부만 보는 1차 검사이며, 원문 근거 일치·지어냄 판정은 사람이 리포트를 보고 확정한다.
 *
 * <p>비교 전에 공백과 쉼표를 지운다 ("2,000만 원"과 "2000만원"을 같게 본다).
 */
final class PolicyQaScorer {

    // "원문에서 확인할 수 없습니다"(프롬프트 규칙 1)와 같은 뜻의 표현
    static final List<String> NOT_IN_SOURCE_MARKERS = List.of(
            "확인할수없", "확인되지않", "나와있지않", "명시되어있지않", "기재되어있지않", "포함되어있지않",
            "안내되어있지않", "찾을수없", "정보가없", "내용이없", "언급되어있지않", "적혀있지않");
    // 판단에 필요한 항목을 확인해야 한다는 안내 (프롬프트 규칙 2, 3)
    static final List<String> NEEDS_CHECK_MARKERS = List.of(
            "확인이필요", "확인해야", "확인하셔야", "확인필요", "확인해주", "확인하시", "확인하신", "확인을받", "확인받",
            "확인하는것이");
    // 이 정책에 대한 질문만 답할 수 있다는 안내 (프롬프트 규칙 4)
    static final List<String> OFF_TOPIC_MARKERS = List.of(
            "이정책", "해당정책", "정책에대한질문", "정책에관한질문", "정책관련", "정책과관련");
    // 자격을 단정하는 표현 (프롬프트 규칙 2 위반 의심). 신청 방법 안내 문장에도 걸릴 수 있어 사람이 다시 본다
    static final List<String> DEFINITIVE_MARKERS = List.of(
            "신청가능합니다", "신청하실수있습니다", "신청할수있습니다", "신청하실수있어요", "대상입니다", "대상이맞습니다",
            "대상이맞아요", "자격이됩니다", "자격이있습니다", "받으실수있습니다", "지원받으실수있습니다");
    // 프롬프트 자리표시자가 답변에 남으면 안 된다
    static final List<String> LEAK_MARKERS = List.of("{{policy}}", "{{profile}}");

    // 프롬프트 규칙 6은 3~6문장. 목록을 쓰면 줄 수가 늘어나므로 넘으면 경고만 남긴다
    static final int MAX_SENTENCES = 8;
    private static final Pattern SENTENCE_END = Pattern.compile("(다|요|죠|까)[.!?](\\s|$)|[.!?]\\s*\\n|[.!?]$");

    private PolicyQaScorer() {
    }

    static Score score(EvalCase evalCase, String answer) {
        String normalized = normalize(answer);
        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (List<String> group : evalCase.expectedFacts()) {
            if (!containsAny(normalized, group)) {
                failures.add("근거 사실 누락: " + String.join(" | ", group));
            }
        }
        for (List<String> group : evalCase.mustInclude()) {
            if (!containsAny(normalized, group)) {
                failures.add("필수 안내 누락: " + String.join(" | ", group));
            }
        }
        for (String forbidden : evalCase.mustNotInclude()) {
            if (normalized.contains(normalize(forbidden))) {
                failures.add("금지 표현 포함: " + forbidden);
            }
        }
        for (String leak : LEAK_MARKERS) {
            if (answer.contains(leak)) {
                failures.add("프롬프트 자리표시자 노출: " + leak);
            }
        }
        if (evalCase.noDefinitiveEligibility()) {
            firstMatch(normalized, DEFINITIVE_MARKERS)
                    .ifPresent(marker -> failures.add("자격 단정 의심: " + marker));
        }
        behaviorFailure(evalCase.behavior(), normalized).ifPresent(failures::add);

        int sentences = countSentences(answer);
        if (sentences > MAX_SENTENCES) {
            warnings.add("문장 수 " + sentences + " (규칙 3~6문장)");
        }
        return new Score(failures.isEmpty(), List.copyOf(failures), List.copyOf(warnings), sentences);
    }

    static String normalize(String text) {
        return text.replaceAll("[\\s,]", "");
    }

    static int countSentences(String answer) {
        Matcher matcher = SENTENCE_END.matcher(answer.strip());
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return Math.max(count, 1);
    }

    private static Optional<String> behaviorFailure(Behavior behavior, String normalized) {
        return switch (behavior) {
            case NOT_IN_SOURCE -> containsAny(normalized, NOT_IN_SOURCE_MARKERS)
                    ? Optional.empty() : Optional.of("'원문에서 확인할 수 없음' 안내 없음");
            case NEEDS_CHECK -> containsAny(normalized, NEEDS_CHECK_MARKERS)
                    ? Optional.empty() : Optional.of("'확인 필요' 안내 없음");
            case OFF_TOPIC -> containsAny(normalized, OFF_TOPIC_MARKERS)
                    ? Optional.empty() : Optional.of("'이 정책 질문만 가능' 안내 없음");
            // 거절은 표현이 다양해 금지 표현(mustNotInclude)으로만 자동 판정한다
            case ANSWER, REFUSE -> Optional.empty();
        };
    }

    private static boolean containsAny(String normalized, List<String> candidates) {
        return firstMatch(normalized, candidates).isPresent();
    }

    private static Optional<String> firstMatch(String normalized, List<String> candidates) {
        return candidates.stream().filter(candidate -> normalized.contains(normalize(candidate))).findFirst();
    }

    /**
     * @param passed    자동 검사를 모두 통과함
     * @param failures  실패 사유 (사람 검토 대상)
     * @param warnings  실패는 아니지만 확인할 점
     */
    record Score(boolean passed, List<String> failures, List<String> warnings, int sentenceCount) {
    }
}
