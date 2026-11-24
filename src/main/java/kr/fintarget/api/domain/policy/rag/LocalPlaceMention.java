package kr.fintarget.api.domain.policy.rag;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyText;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 질문이 시·군·구 한정 정책의 지역을 직접 말했는지 간단한 문자열 비교로 판정한다 (#235).
 * 통합 답변은 시·군·구 한정 정책을 후보에서 빼지만, "강진군 청년 주거비", "용인에서 받을 수 있는…"처럼 그 지역을 말한 질문에는 남긴다.
 *
 * <ul>
 *   <li>정책명의 "○○시/군/구"(예: "곡성군 일하잡", "(광양시)광양학사")와 정책명 앞 "○○청년"(예: "용인청년 부동산…")의 ○○:
 *       질문에 전체 이름("곡성군")이 있거나, 두 글자 이상인 ○○("곡성", "용인")이 지명처럼 쓰였으면(뒤에 띄어쓰기·조사·"청년"·"시/군/구") 말한 것으로 본다</li>
 *   <li>정책명에 지명이 없으면 설명·자격 앞부분({@link #TEXT_SCAN_LENGTH}자)의 "○○시/군/구": 질문에 전체 이름("강진군")이 있을 때만 인정한다.
 *       본문에는 "지원시", "선정시" 같은 일반 낱말도 같은 모양으로 나와서 앞부분만으로는 판정하지 않는다</li>
 *   <li>한 글자 앞부분("서구", "남구")은 전체 이름이 있을 때만 인정한다</li>
 * </ul>
 */
final class LocalPlaceMention {

    static final int TEXT_SCAN_LENGTH = 400;

    private static final Pattern PLACE = Pattern.compile("([가-힣]{1,4}?)(시|군|구)(?=$|[^가-힣]|청년|에|의)");
    private static final Pattern NAME_PREFIX = Pattern.compile("^[\\s(（\\[]*([가-힣]{2,3})청년");
    private static final String STEM_FOLLOWERS = "(?:$|\\s|시|군|구|에|의|청년|사는|거주|지역|쪽)";
    // 지명처럼 보이지만 일반 낱말인 앞부분
    private static final Set<String> STOP_STEMS = Set.of(
            "해당", "이사", "신청", "대상", "입주", "전입", "당시", "제출", "경우", "거주", "주소", "관할", "소재", "인근", "전체",
            "지원", "선정", "계약", "접수", "모집", "발급", "가입", "졸업", "취업", "채용", "부산", "서울", "대구", "인천", "광주", "대전", "울산");

    private LocalPlaceMention() {
    }

    /** 시·군·구 한정 정책을 빼되, 질문이 그 지역을 말한 정책은 남긴다 */
    static List<Policy> filter(List<Policy> candidates, String question, Predicate<Policy> isLocal) {
        return candidates.stream().filter(policy -> !isLocal.test(policy) || mentioned(policy, question)).toList();
    }

    static boolean mentioned(Policy policy, String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        Set<Place> fromName = nameplaces(policy.getName());
        if (!fromName.isEmpty()) {
            for (Place place : fromName) {
                if (question.contains(place.full()) || stemMentioned(place.stem(), question)) {
                    return true;
                }
            }
            return false;
        }
        for (Place place : find(textOf(policy))) {
            if (question.contains(place.full())) {
                return true;
            }
        }
        return false;
    }

    static Set<Place> nameplaces(String name) {
        Set<Place> places = find(name);
        if (name != null) {
            Matcher prefix = NAME_PREFIX.matcher(name);
            if (prefix.find() && !STOP_STEMS.contains(prefix.group(1))) {
                places.add(new Place(prefix.group(1), prefix.group(1)));
            }
        }
        return places;
    }

    private static boolean stemMentioned(String stem, String question) {
        return stem.length() >= 2 && Pattern.compile(Pattern.quote(stem) + STEM_FOLLOWERS).matcher(question).find();
    }

    private static String textOf(Policy policy) {
        PolicyText text = policy.getText();
        String scan = String.join(" ",
                policy.getDescription() == null ? "" : policy.getDescription(),
                text == null || text.getEligibilityDetail() == null ? "" : text.getEligibilityDetail());
        return scan.length() > TEXT_SCAN_LENGTH ? scan.substring(0, TEXT_SCAN_LENGTH) : scan;
    }

    static Set<Place> find(String value) {
        Set<Place> places = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return places;
        }
        // 낱말 단위로 찾는다 ("광양학사"의 "광양학"처럼 낱말 중간에서 끊지 않게)
        for (String token : value.split("[^가-힣]+")) {
            Matcher matcher = PLACE.matcher(token);
            if (matcher.lookingAt() && matcher.end() >= token.length() - 2) {
                String stem = matcher.group(1);
                if (!STOP_STEMS.contains(stem)) {
                    places.add(new Place(stem + matcher.group(2), stem));
                }
            }
        }
        return places;
    }

    record Place(String full, String stem) {
    }
}
