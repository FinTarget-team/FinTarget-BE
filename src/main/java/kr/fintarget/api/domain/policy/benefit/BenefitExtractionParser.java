package kr.fintarget.api.domain.policy.benefit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AI 응답(JSON)을 검증해 {@link BenefitExtraction}으로 바꾼다 (#162).
 *
 * <p>잘못된 금액이 저장되면 시뮬레이션 달성일이 틀리므로, 의심스러운 CASH 결과는 버리지 않고 UNKNOWN으로 낮춘다
 * (UNKNOWN은 시뮬레이션에 반영되지 않는다). 낮춘 이유는 validationNote에 남긴다.
 * <ul>
 *   <li>근거 문장(evidence)이 원문에 없음 — 금액을 지어냈을 가능성</li>
 *   <li>금액이 없거나 상한을 넘음</li>
 * </ul>
 * JSON이 아니거나 유형을 알 수 없으면 {@link BenefitExtractionException}을 던진다.
 */
public final class BenefitExtractionParser {

    // 상한: 원문 표본(2026-11) 최대값은 월 100만원, 1회 1,000만원. 단위 착오(만원↔원)를 잡을 만큼 넉넉하게 둔다
    static final long MAX_MONTHLY_AMOUNT = 5_000_000L;
    static final long MAX_ONE_TIME_AMOUNT = 50_000_000L;
    static final int MAX_MONTHS = 120;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private BenefitExtractionParser() {
    }

    public static BenefitExtraction parse(String aiText, BenefitSource source) {
        JsonNode node = readJson(aiText);
        BenefitType type = readType(node);
        String evidence = text(node, "evidence");

        if (!type.reflectsInSimulation()) {
            return BenefitExtraction.of(type, evidence, null);
        }

        Long monthly = positiveLong(node, "monthlyAmount");
        Long oneTime = positiveLong(node, "oneTimeAmount");
        Integer months = monthly == null ? null : positiveInt(node, "months");
        boolean isMaximum = node.path("isMaximum").asBoolean(false);

        if (monthly == null && oneTime == null) {
            return BenefitExtraction.of(BenefitType.UNKNOWN, evidence, "CASH인데 금액이 없음");
        }
        if ((monthly != null && monthly > MAX_MONTHLY_AMOUNT) || (oneTime != null && oneTime > MAX_ONE_TIME_AMOUNT)) {
            return BenefitExtraction.of(BenefitType.UNKNOWN, evidence,
                    "금액 상한 초과 monthly=" + monthly + " oneTime=" + oneTime);
        }
        if (months != null && months > MAX_MONTHS) {
            return BenefitExtraction.of(BenefitType.UNKNOWN, evidence, "기간 상한 초과 months=" + months);
        }
        if (evidence == null || !containsIgnoringSpace(source.toPromptText(), evidence)) {
            return BenefitExtraction.of(BenefitType.UNKNOWN, evidence, "근거 문장이 원문에 없음");
        }
        return new BenefitExtraction(type, monthly, months, oneTime, isMaximum, evidence, null);
    }

    private static JsonNode readJson(String aiText) {
        if (aiText == null) {
            throw new BenefitExtractionException("AI 응답이 비어 있습니다.");
        }
        // 코드 블록이나 앞뒤 설명이 붙어도 첫 { 부터 마지막 } 까지만 읽는다
        int start = aiText.indexOf('{');
        int end = aiText.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BenefitExtractionException("AI 응답에 JSON 객체가 없습니다.");
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(aiText.substring(start, end + 1));
            if (!node.isObject()) {
                throw new BenefitExtractionException("AI 응답이 JSON 객체가 아닙니다.");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new BenefitExtractionException("AI 응답 JSON을 읽을 수 없습니다.", e);
        }
    }

    private static BenefitType readType(JsonNode node) {
        String type = text(node, "type");
        if (type == null) {
            throw new BenefitExtractionException("AI 응답에 type이 없습니다.");
        }
        try {
            return BenefitType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BenefitExtractionException("알 수 없는 혜택 유형입니다: " + type, e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().strip();
        return text.isEmpty() ? null : text;
    }

    // 숫자 또는 숫자 문자열("300,000")을 받는다. 0 이하와 해석할 수 없는 값은 null
    private static Long positiveLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        long number;
        if (value.isNumber()) {
            number = Math.round(value.asDouble());
        } else {
            String digits = value.asText().replace(",", "").strip();
            if (!digits.matches("\\d+")) {
                return null;
            }
            number = Long.parseLong(digits);
        }
        return number > 0 ? number : null;
    }

    private static Integer positiveInt(JsonNode node, String field) {
        Long value = positiveLong(node, field);
        return value == null || value > Integer.MAX_VALUE ? null : value.intValue();
    }

    static boolean containsIgnoringSpace(String haystack, String needle) {
        return strip(haystack).contains(strip(needle));
    }

    // 공백과 원문 정제 과정에서 바뀔 수 있는 구분 기호(" / ")를 지워 비교한다
    private static String strip(String value) {
        return value.replaceAll("[\\s/]", "");
    }
}
