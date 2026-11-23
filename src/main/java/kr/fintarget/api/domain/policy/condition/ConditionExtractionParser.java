package kr.fintarget.api.domain.policy.condition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.fintarget.api.domain.user.code.Region;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 응답(JSON)을 {@link ConditionExtraction}으로 바꾼다 (#220).
 *
 * <p>잘못 저장되면 신청할 수 있는 사용자에게 정책이 보이지 않으므로, 해석할 수 없는 값은 버리지 않고 모름 쪽으로 낮춘다.
 * <ul>
 *   <li>필드가 없거나 정의되지 않은 enum 값이면 그 필드만 UNKNOWN</li>
 *   <li>INDIVIDUAL인데 상한 금액·기간을 해석할 수 없거나 범위를 벗어나면 소득 기준을 UNKNOWN으로 낮춤 (금액 없이 INDIVIDUAL을 남기지 않음)</li>
 *   <li>지역 코드 중 정의되지 않은 값이 하나라도 있으면 지역 전체를 빈 목록으로 (일부만 남기면 다른 지역 사용자가 잘못 제외될 수 있음)</li>
 * </ul>
 * JSON 객체가 아니면 {@link ConditionExtractionException}을 던진다.
 */
public final class ConditionExtractionParser {

    // 본인 월 소득 상한의 허용 범위 (원). 단위 착오(만원↔원)를 걸러낸다. 원문 표본 최대값은 연 7천만원(월 약 583만원) 수준
    static final long MIN_MONTHLY_INCOME = 300_000L;
    static final long MAX_MONTHLY_INCOME = 30_000_000L;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ConditionExtractionParser() {
    }

    public static ConditionExtraction parse(String aiText) {
        JsonNode node = readJson(aiText);
        IncomeBasis incomeBasis = enumValue(node, "incomeBasis", IncomeBasis.class, IncomeBasis.UNKNOWN);
        Long incomeMaxMonthly = null;
        if (incomeBasis == IncomeBasis.INDIVIDUAL) {
            incomeMaxMonthly = monthlyIncome(node);
            if (incomeMaxMonthly == null) {
                incomeBasis = IncomeBasis.UNKNOWN;
            }
        }
        return new ConditionExtraction(
                incomeBasis,
                incomeMaxMonthly,
                enumValue(node, "housingRequirement", HousingRequirement.class, HousingRequirement.UNKNOWN),
                enumValue(node, "targetType", TargetType.class, TargetType.UNKNOWN),
                regions(node));
    }

    // 원문 금액과 기간(ANNUAL/MONTHLY)으로 월 상한을 계산한다. 연소득은 12로 나누고 원 미만은 버린다
    private static Long monthlyIncome(JsonNode node) {
        Long amount = positiveLong(node.get("incomeMaxAmount"));
        String period = text(node, "incomePeriod");
        if (amount == null || period == null) {
            return null;
        }
        long monthly = switch (period.trim().toUpperCase()) {
            case "ANNUAL" -> amount / 12;
            case "MONTHLY" -> amount;
            default -> -1L;
        };
        return monthly >= MIN_MONTHLY_INCOME && monthly <= MAX_MONTHLY_INCOME ? monthly : null;
    }

    private static List<String> regions(JsonNode node) {
        JsonNode value = node.get("extractedRegions");
        if (value == null || !value.isArray()) {
            return List.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        for (JsonNode item : value) {
            String code = item.isTextual() ? item.asText().trim().toUpperCase() : null;
            if (code == null || code.isEmpty() || Region.fromCode(code) == null) {
                return List.of();
            }
            codes.add(code);
        }
        return new ArrayList<>(codes);
    }

    private static JsonNode readJson(String aiText) {
        if (aiText == null) {
            throw new ConditionExtractionException("AI 응답이 비어 있습니다.");
        }
        // 코드 블록이나 앞뒤 설명이 붙어도 첫 { 부터 마지막 } 까지만 읽는다 (혜택 추출 파서와 같은 방식)
        int start = aiText.indexOf('{');
        int end = aiText.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ConditionExtractionException("AI 응답에 JSON 객체가 없습니다.");
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(aiText.substring(start, end + 1));
            if (!node.isObject()) {
                throw new ConditionExtractionException("AI 응답이 JSON 객체가 아닙니다.");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new ConditionExtractionException("AI 응답 JSON을 읽을 수 없습니다.", e);
        }
    }

    private static <E extends Enum<E>> E enumValue(JsonNode node, String field, Class<E> type, E fallback) {
        String value = text(node, field);
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String text = value.asText().strip();
        return text.isEmpty() ? null : text;
    }

    // 숫자 또는 숫자 문자열("50,000,000")을 받는다. 0 이하와 해석할 수 없는 값은 null
    private static Long positiveLong(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        long number;
        if (value.isNumber()) {
            number = Math.round(value.asDouble());
        } else if (value.isTextual()) {
            String digits = value.asText().replace(",", "").strip();
            if (!digits.matches("\\d{1,15}")) {
                return null;
            }
            number = Long.parseLong(digits);
        } else {
            return null;
        }
        return number > 0 ? number : null;
    }
}
