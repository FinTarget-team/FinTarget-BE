package kr.fintarget.api.domain.policy.recommend.rerank;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 리랭킹 응답(JSON)을 검증해 고른 후보 번호를 꺼낸다 (#229).
 * <ul>
 *   <li>JSON 객체가 아니거나 selected 배열이 없으면 {@link PolicyRerankParseException}</li>
 *   <li>항목이 객체가 아니거나, policyId가 후보 번호(1~후보 수)가 아니거나, 이미 나온 번호면 그 항목만 버리고 버린 수를 센다</li>
 *   <li>policyId는 숫자, 숫자 문자열("3"), 대괄호를 붙인 문자열("[3]")을 받는다</li>
 *   <li>최대 limit건까지만 쓴다 (넘는 항목은 버린 수에 넣지 않는다)</li>
 *   <li>reason은 앞뒤 공백을 지우고 200자로 자른다. 없으면 빈 문자열 (응답에는 쓰지 않고 캐시·로컬 확인용)</li>
 * </ul>
 */
public final class PolicyRerankParser {

    static final int REASON_MAX = 200;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PolicyRerankParser() {
    }

    /**
     * @param candidateCount 후보 수 (번호는 1부터)
     * @param limit          최대 선택 수 (N)
     */
    public static Selection parse(String aiText, int candidateCount, int limit) {
        JsonNode selected = readJson(aiText).get("selected");
        if (selected == null || !selected.isArray()) {
            throw new PolicyRerankParseException("selected 배열이 없습니다");
        }
        List<Integer> numbers = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        int dropped = 0;
        for (JsonNode item : selected) {
            if (numbers.size() >= limit) {
                break;
            }
            Integer number = item.isObject() ? number(item.get("policyId")) : null;
            if (number == null || number < 1 || number > candidateCount || !seen.add(number)) {
                dropped++;
                continue;
            }
            numbers.add(number);
            reasons.add(reason(item.get("reason")));
        }
        return new Selection(List.copyOf(numbers), List.copyOf(reasons), dropped);
    }

    private static JsonNode readJson(String aiText) {
        if (aiText == null) {
            throw new PolicyRerankParseException("응답이 비어 있습니다");
        }
        // 지시와 달리 코드 블록이나 앞뒤 문장을 붙여도 첫 { 부터 마지막 } 까지만 읽는다
        int start = aiText.indexOf('{');
        int end = aiText.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new PolicyRerankParseException("JSON 객체가 아닙니다");
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(aiText.substring(start, end + 1));
            if (node == null || !node.isObject()) {
                throw new PolicyRerankParseException("JSON 객체가 아닙니다");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new PolicyRerankParseException("JSON을 해석할 수 없습니다");
        }
    }

    private static Integer number(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isIntegralNumber() && value.canConvertToInt()) {
            return value.intValue();
        }
        if (value.isTextual()) {
            String text = value.asText().strip();
            if (text.startsWith("[") && text.endsWith("]")) {
                text = text.substring(1, text.length() - 1).strip();
            }
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String reason(JsonNode value) {
        if (value == null || !value.isTextual()) {
            return "";
        }
        String text = value.asText().strip();
        return text.length() <= REASON_MAX ? text : text.substring(0, REASON_MAX);
    }

    /**
     * @param numbers 고른 후보 번호 (적합한 순서, 1부터)
     * @param reasons numbers와 같은 순서의 선택 이유
     * @param dropped 버린 항목 수 (형식 오류, 후보 밖 번호, 중복)
     */
    public record Selection(List<Integer> numbers, List<String> reasons, int dropped) {
    }
}
