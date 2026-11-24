package kr.fintarget.api.domain.policy.search;

import kr.fintarget.api.domain.policy.entity.Policy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 질문에 정책 이름이 들어 있는지 찾는다 (#235, 통합 정책 AI 답변 전용).
 * 짧은 질문("머물자리론 지금 신청 가능해?", "K-패스")은 벡터 거리가 멀게 나와 검색되지 않을 수 있어, 이름이 맞으면 검색 결과 앞에 넣는다.
 *
 * <p>비교는 띄어쓰기·기호를 지우고 소문자로 바꾼 문자열의 부분 일치로 한다. 정책마다 아래 이름을 키로 쓴다.
 * <ul>
 *   <li>전체 이름, 첫 괄호 앞 이름, 괄호 안 별칭 (예: "부산 청년 임차보증금 대출 및 대출이자 지원(머물자리론)"의 "머물자리론",
 *       "K-패스(K패스)"의 "k패스")</li>
 *   <li>앞에 붙은 연도("2026", "2026년")와 앞쪽 괄호 머리말("(광양시)")을 뗀 이름</li>
 * </ul>
 * 짧거나 일반적인 이름의 오탐을 막기 위해 키는 정리한 길이가 {@link #MIN_KEY_LENGTH}자 이상이고,
 * 일반 단어({@link #GENERIC_WORDS})를 지우고도 {@link #MIN_SPECIFIC_LENGTH}자 이상 남아야 한다
 * (예: "청년 지원", "청년수당"은 키가 되지 않음. "K-패스"는 "k패스" 3자라 키가 됨).
 * 괄호 안 별칭은 {@link #MIN_ALIAS_LENGTH}자 이상만 쓴다 ("(생활비)", "(등록금)" 같은 일반 낱말이 많음).
 */
public final class PolicyNameMatch {

    static final int MIN_KEY_LENGTH = 3;
    static final int MIN_ALIAS_LENGTH = 4;
    static final int MIN_SPECIFIC_LENGTH = 2;
    /** 한 질문에서 앞에 넣는 최대 정책 수 */
    static final int MAX_MATCHES = 2;

    static final List<String> GENERIC_WORDS = List.of(
            "청년", "지원", "사업", "정책", "운영", "서비스", "공급", "프로그램", "센터", "수당", "제도", "확대", "강화");

    private static final Pattern NON_WORD = Pattern.compile("[^0-9a-z가-힣]");
    private static final Pattern PARENTHESIS = Pattern.compile("[(（\\[]([^)）\\]]*)[)）\\]]");
    private static final Pattern LEADING_YEAR = Pattern.compile("^\\s*(?:19|20)\\d{2}\\s*년?\\s*");
    private static final Pattern LEADING_BRACKET = Pattern.compile("^\\s*[(（\\[][^)）\\]]*[)）\\]]\\s*");

    private PolicyNameMatch() {
    }

    /**
     * 이름이 질문에 들어 있는 정책을 가장 긴 키 순서로 최대 {@link #MAX_MATCHES}건 돌려준다.
     */
    public static List<Policy> find(List<Policy> candidates, String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        String normalizedQuestion = normalize(question);
        List<Scored> scored = new ArrayList<>();
        for (Policy policy : candidates) {
            int best = 0;
            for (String key : keys(policy.getName())) {
                if (key.length() > best && normalizedQuestion.contains(key)) {
                    best = key.length();
                }
            }
            if (best > 0) {
                scored.add(new Scored(policy, best));
            }
        }
        scored.sort(Comparator.comparingInt(Scored::length).reversed());
        return scored.stream().limit(MAX_MATCHES).map(Scored::policy).toList();
    }

    /** 정책 이름에서 비교에 쓸 키 (정리·필터 적용 후) */
    static Set<String> keys(String name) {
        Set<String> keys = new LinkedHashSet<>();
        if (name == null || name.isBlank()) {
            return keys;
        }
        List<String> raw = new ArrayList<>();
        raw.add(name);
        String withoutLeading = LEADING_YEAR.matcher(LEADING_BRACKET.matcher(name).replaceFirst("")).replaceFirst("");
        raw.add(withoutLeading);
        for (String base : List.of(name, withoutLeading)) {
            int paren = indexOfParenthesis(base);
            if (paren > 0) {
                raw.add(base.substring(0, paren));
            }
        }
        for (String value : raw) {
            String key = normalize(value);
            if (isUsable(key)) {
                keys.add(key);
            }
        }
        // 괄호 안 별칭은 "(생활비)", "(등록금)"처럼 일반 낱말이 많아 더 길어야 쓴다 ("머물자리론"은 5자)
        Matcher alias = PARENTHESIS.matcher(name);
        while (alias.find()) {
            String key = normalize(alias.group(1));
            if (key.length() >= MIN_ALIAS_LENGTH && isUsable(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    static boolean isUsable(String key) {
        if (key.length() < MIN_KEY_LENGTH) {
            return false;
        }
        String specific = key;
        for (String word : GENERIC_WORDS) {
            specific = specific.replace(word, "");
        }
        return specific.length() >= MIN_SPECIFIC_LENGTH;
    }

    static String normalize(String value) {
        return NON_WORD.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private static int indexOfParenthesis(String value) {
        int min = -1;
        for (char c : new char[]{'(', '（', '['}) {
            int index = value.indexOf(c);
            if (index >= 0 && (min < 0 || index < min)) {
                min = index;
            }
        }
        return min;
    }

    private record Scored(Policy policy, int length) {
    }
}
