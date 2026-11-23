package kr.fintarget.api.domain.policy.recommend;

import java.util.regex.Pattern;

/**
 * 비슷한 이름의 정책을 묶는 키 (#225). 추천 상위 N에서 같은 정책의 변형이 자리를 차지하지 않게 한다.
 * 예: "생활안정자금 융자(부모요양비)"·"생활안정자금 융자(혼례비)", "2025년 중구 소상공인 경영안정자금 지원 특례보증"·"2025년 북구 …",
 * "전세보증금반환보증 보증료 지원"·"부산 전세보증금 반환보증 보증료 지원".
 *
 * <p>규칙: 앞쪽 괄호 머리말(예: "(옹진군)")을 빼고, 나머지 이름에서 첫 괄호부터 뒤를 버린 뒤(괄호 앞 이름),
 * 이름 앞쪽에 붙은 연도·지역 낱말(시·도 이름, "○○시·군·구")을 지우고 공백·기호를 없앤다.
 * 지역 낱말은 이름 앞쪽에서만 지운다 ("1인 가구", "전시"처럼 시·군·구로 끝나는 일반 낱말을 지우지 않게).
 */
final class SimilarPolicyName {

    private static final Pattern LEADING_BRACKET = Pattern.compile("^\\s*[(\\[【<〈][^)\\]】>〉]*[)\\]】>〉]\\s*");
    private static final Pattern YEAR = Pattern.compile("(?:19|20)\\d{2}\\s*년?|'\\d{2}\\s*년?|‘\\d{2}\\s*년?");
    private static final Pattern SIDO = Pattern.compile(
            "(서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주|충청북|충청남|전라북|전라남|경상북|경상남)"
                    + "(특별자치시|특별자치도|특별시|광역시|도)?");
    private static final Pattern LOCAL_TOKEN = Pattern.compile("^[가-힣]{1,4}(시|군|구)$");
    private static final Pattern NON_WORD = Pattern.compile("[^0-9A-Za-z가-힣]");

    private SimilarPolicyName() {
    }

    static String key(String name) {
        if (name == null) {
            return "";
        }
        String base = LEADING_BRACKET.matcher(name).replaceFirst("");
        int paren = indexOfAny(base, "(（[");
        if (paren > 0) {
            base = base.substring(0, paren);
        }
        String[] tokens = base.trim().split("\\s+");
        int start = 0;
        while (start < tokens.length - 1 && isPrefixToken(tokens[start])) {
            start++;
        }
        StringBuilder kept = new StringBuilder();
        for (int i = start; i < tokens.length; i++) {
            kept.append(tokens[i]);
        }
        String key = NON_WORD.matcher(kept.toString()).replaceAll("");
        // 지역 낱말만 있는 이름처럼 모두 지워지면 원래 이름으로 구분한다
        return key.isEmpty() ? NON_WORD.matcher(name).replaceAll("") : key;
    }

    // 이름 앞에 붙는 연도("2025년", "'26") 또는 지역 낱말("부산", "경상북도", "중구", "화순군")
    private static boolean isPrefixToken(String token) {
        return YEAR.matcher(token).matches() || SIDO.matcher(token).matches() || LOCAL_TOKEN.matcher(token).matches();
    }

    private static int indexOfAny(String text, String chars) {
        int min = -1;
        for (char c : chars.toCharArray()) {
            int index = text.indexOf(c);
            if (index >= 0 && (min < 0 || index < min)) {
                min = index;
            }
        }
        return min;
    }
}
