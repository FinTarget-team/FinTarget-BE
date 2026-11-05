package kr.fintarget.api.domain.policy.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 모델 답변에서 근거 번호([1], [1, 2], [1][3])를 꺼내고 본문에서 지운다 (#183).
 * 근거는 응답의 sources로 따로 내려가고 앱은 답변을 일반 텍스트로 보여주므로, 본문에는 번호를 남기지 않는다.
 *
 * <p>번호는 프롬프트에 넣은 청크 순서(1부터)다. 1~sourceCount 밖의 번호는 근거로 쓰지 않고 invalidCount로 센다.
 */
public final class PolicyCitationParser {

    // 앞 공백까지 함께 지운다: "지원합니다 [1]." → "지원합니다."
    private static final Pattern CITATION = Pattern.compile("[ \\t]*\\[(\\d{1,3}(?:\\s*,\\s*\\d{1,3})*)]");
    private static final Pattern SPACE_BEFORE_PUNCTUATION = Pattern.compile("[ \\t]+([.,!?)])");
    private static final Pattern SPACES = Pattern.compile("[ \\t]{2,}");

    private PolicyCitationParser() {
    }

    /**
     * @param text        번호를 지운 답변 (마크다운 정리 전)
     * @param cited       유효한 근거 번호. 처음 나온 순서, 중복 없음
     * @param invalidCount 1~sourceCount 밖이라 버린 번호 수 (중복 포함)
     */
    public record Result(String text, List<Integer> cited, int invalidCount) {

        public boolean hasCitation() {
            return !cited.isEmpty();
        }
    }

    public static Result parse(String answer, int sourceCount) {
        if (answer == null) {
            return new Result("", List.of(), 0);
        }
        Set<Integer> cited = new LinkedHashSet<>();
        int invalid = 0;
        Matcher matcher = CITATION.matcher(answer);
        StringBuilder removed = new StringBuilder();
        while (matcher.find()) {
            for (String token : matcher.group(1).split(",")) {
                int number = Integer.parseInt(token.strip());
                if (number >= 1 && number <= sourceCount) {
                    cited.add(number);
                } else {
                    invalid++;
                }
            }
            matcher.appendReplacement(removed, "");
        }
        matcher.appendTail(removed);
        return new Result(tidy(removed.toString()), List.copyOf(cited), invalid);
    }

    // 번호를 지운 자리에 남은 공백을 줄 단위로 정리한다
    private static String tidy(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            String cleaned = SPACE_BEFORE_PUNCTUATION.matcher(line).replaceAll("$1");
            cleaned = SPACES.matcher(cleaned).replaceAll(" ").stripTrailing();
            lines.add(cleaned);
        }
        return String.join("\n", lines).strip();
    }
}
