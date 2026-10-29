package kr.fintarget.api.domain.policy.mapper;

import org.springframework.web.util.HtmlUtils;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 정책 원문 텍스트 정제 (#113).
 * "없음", "해당 없음" 같은 표현은 조건이 없다는 원문 정보이므로 그대로 둔다.
 */
public final class PolicyTextCleaner {

    private PolicyTextCleaner() {
    }

    /**
     * 원문 컬럼용 정제. HTML 엔티티 디코딩, 줄바꿈 통일(\r\n, \r → \n), 줄마다 앞뒤 공백 제거,
     * 연속된 빈 줄을 하나로 줄이고 전체 앞뒤 공백을 제거한다. 결과가 빈 값이거나 "-"이면 null.
     */
    public static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String text = HtmlUtils.htmlUnescape(raw).replace("\r\n", "\n").replace('\r', '\n');
        text = Arrays.stream(text.split("\n", -1))
                .map(String::strip)
                .collect(Collectors.joining("\n"))
                .replaceAll("\n{3,}", "\n\n")
                .strip();
        if (text.isEmpty() || text.equals("-")) {
            return null;
        }
        return text;
    }

    /**
     * 기존 description용. HTML 엔티티만 디코딩하고 공백·줄바꿈은 건드리지 않는다.
     */
    public static String unescapeOnly(String raw) {
        return raw == null ? null : HtmlUtils.htmlUnescape(raw);
    }

    /**
     * "라벨: 값" 줄을 이어 붙인다. 값을 clean한 결과가 null인 줄은 통째로 뺀다. 모든 줄이 빠지면 null.
     * labelsAndValues는 라벨, 값, 라벨, 값 ... 순서다.
     */
    public static String labeledLines(String... labelsAndValues) {
        if (labelsAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("라벨과 값은 쌍으로 넘겨야 합니다");
        }
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < labelsAndValues.length; i += 2) {
            String value = clean(labelsAndValues[i + 1]);
            if (value == null) {
                continue;
            }
            if (!joined.isEmpty()) {
                joined.append('\n');
            }
            joined.append(labelsAndValues[i]).append(": ").append(value);
        }
        return joined.isEmpty() ? null : joined.toString();
    }

    /**
     * 비어 있지 않은 조각만 줄바꿈으로 잇는다. 모두 비면 null.
     */
    static String joinLines(String... parts) {
        String joined = Arrays.stream(parts)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
        return joined.isEmpty() ? null : joined;
    }
}
