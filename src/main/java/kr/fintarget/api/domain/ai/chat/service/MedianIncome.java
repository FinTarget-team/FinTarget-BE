package kr.fintarget.api.domain.ai.chat.service;

import kr.fintarget.api.domain.user.code.IncomeBracket;

import java.time.Year;
import java.util.Map;

/**
 * 1인 가구 기준 중위소득 대비 소득 구간 (#216). 정책 원문의 "기준 중위소득 ○○% 이하" 조건을 AI가 판단할 수 있도록
 * 사용자 월 소득 구간을 비율 구간으로 바꾼다.
 *
 * <p>가구원 수를 받지 않아 1인 가구로 가정한 참고값이다. 정책의 중위소득 조건은 보통 가구 소득인정액(재산 환산 포함)이나
 * 건강보험료로 판정하므로, 프롬프트(policy-qa-v3)에서 가정을 밝히고 단정하지 않도록 한다.
 *
 * <p>기준 중위소득은 매년 보건복지부가 고시한다. 표에 없는 연도는 null을 돌려주며, 이때 프롬프트에는 "확인 필요"가 들어간다.
 * 매년 고시 후(보통 7~8월 의결, 다음 해 1월 적용) 다음 연도 값을 추가한다.
 */
public final class MedianIncome {

    // 1인 가구 월 기준 중위소득 (원). 2026: 보건복지부 고시, 2027: 제80차 중앙생활보장위원회 의결 (2026-07-28)
    static final Map<Integer, Long> ONE_PERSON = Map.of(
            2026, 2_564_238L,
            2027, 2_736_042L);

    private MedianIncome() {
    }

    /**
     * @return "58% 이상 102% 미만"처럼 하한은 내림, 상한은 올림한 비율 구간. 구간이나 연도 값이 없으면 null
     */
    public static String ratioLabel(IncomeBracket bracket, Year year) {
        Long median = ONE_PERSON.get(year.getValue());
        if (bracket == null || median == null) {
            return null;
        }
        // 경계에서 조건을 충족한다고 잘못 판단하지 않도록 구간을 바깥쪽으로 반올림한다
        long min = (long) Math.floor(bracket.getMinIncome() * 100.0 / median);
        if (bracket.getMaxIncome() == null) {
            return min + "% 이상";
        }
        long max = (long) Math.ceil(bracket.getMaxIncome() * 100.0 / median);
        return bracket.getMinIncome() == 0 ? max + "% 미만" : min + "% 이상 " + max + "% 미만";
    }
}
