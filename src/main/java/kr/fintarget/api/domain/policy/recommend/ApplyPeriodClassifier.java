package kr.fintarget.api.domain.policy.recommend;

import kr.fintarget.api.domain.policy.entity.PolicyText;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 정책의 신청 기간을 오늘 날짜와 비교해 {@link ApplyPeriodStatus}로 판정한다 (#220). 추천 정렬에만 쓴다.
 *
 * <p>신청 기간(PolicyText.applyPeriod)과 온통청년 신청기간 구분 코드(applyPeriodCode)만 본다.
 * 사업 기간은 신청 기간이 아니라서 쓰지 않는다 (policy-qa-v3와 같은 방침).
 *
 * <p>판정 규칙 (위에서부터 먼저 맞는 것):
 * <ol>
 *   <li>온통청년 코드 0057003(마감) → NOT_OPEN, 0057002(상시) → OPEN</li>
 *   <li>온통청년 "YYYYMMDD ~ YYYYMMDD" (여러 기간이면 줄바꿈 구분) → 오늘이 한 기간이라도 안에 있으면 OPEN, 아니면 NOT_OPEN</li>
 *   <li>"~" 앞뒤의 날짜 (KINFA "2026.1.15 ~ 자금소진시까지", "2025년 1월~12월 예산소진시까지", "~ 2026. 12. 31." 등):
 *       종료일이 지났거나 시작일이 오늘 뒤면 NOT_OPEN, 아니면 OPEN. 일이 없으면 시작은 1일, 종료는 말일로 본다</li>
 *   <li>날짜 없이 "상시", "연중", "수시", "소진" → OPEN</li>
 *   <li>그 외("기관 문의", 빈 값, 해석할 수 없는 형식) → UNKNOWN</li>
 * </ol>
 */
public final class ApplyPeriodClassifier {

    static final String CODE_SPECIFIC = "0057001";
    static final String CODE_ALWAYS = "0057002";
    static final String CODE_CLOSED = "0057003";

    private static final Pattern YMD_RANGE = Pattern.compile("(\\d{8})\\s*~\\s*(\\d{8})");
    // 연·월·(일). 구분자는 . - / 년 월 일과 공백
    private static final Pattern DATE = Pattern.compile(
            "(\\d{4})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]?\\s*(?:(\\d{1,2})\\s*[.일]?)?");
    // 연도 없이 월만 (예: "2025년 1월~12월"의 "12월")
    private static final Pattern MONTH_ONLY = Pattern.compile("^\\s*(\\d{1,2})\\s*월");
    private static final Pattern ALWAYS_WORDS = Pattern.compile("상시|연중|수시|소진");

    private ApplyPeriodClassifier() {
    }

    public static ApplyPeriodStatus classify(PolicyText text, LocalDate today) {
        if (text == null) {
            return ApplyPeriodStatus.UNKNOWN;
        }
        String code = text.getApplyPeriodCode();
        if (CODE_CLOSED.equals(code)) {
            return ApplyPeriodStatus.NOT_OPEN;
        }
        if (CODE_ALWAYS.equals(code)) {
            return ApplyPeriodStatus.OPEN;
        }
        return classify(text.getApplyPeriod(), today);
    }

    static ApplyPeriodStatus classify(String period, LocalDate today) {
        if (period == null || period.isBlank()) {
            return ApplyPeriodStatus.UNKNOWN;
        }
        String normalized = period.replace('∼', '~').replace('～', '~');

        List<LocalDate[]> ymdRanges = ymdRanges(normalized);
        if (!ymdRanges.isEmpty()) {
            boolean open = ymdRanges.stream().anyMatch(range -> !today.isBefore(range[0]) && !today.isAfter(range[1]));
            return open ? ApplyPeriodStatus.OPEN : ApplyPeriodStatus.NOT_OPEN;
        }

        int tilde = normalized.indexOf('~');
        if (tilde >= 0) {
            String left = normalized.substring(0, tilde);
            String right = normalized.substring(tilde + 1);
            LocalDate start = firstDate(left, false);
            LocalDate end = firstDate(right, true);
            if (end == null && start != null) {
                end = monthOnlyEnd(right, start.getYear());
            }
            if (start != null || end != null) {
                if (end != null && end.isBefore(today)) {
                    return ApplyPeriodStatus.NOT_OPEN;
                }
                if (start != null && start.isAfter(today)) {
                    return ApplyPeriodStatus.NOT_OPEN;
                }
                return ApplyPeriodStatus.OPEN;
            }
        }
        return ALWAYS_WORDS.matcher(normalized).find() ? ApplyPeriodStatus.OPEN : ApplyPeriodStatus.UNKNOWN;
    }

    private static List<LocalDate[]> ymdRanges(String period) {
        List<LocalDate[]> ranges = new ArrayList<>();
        Matcher matcher = YMD_RANGE.matcher(period);
        while (matcher.find()) {
            LocalDate start = ymd(matcher.group(1));
            LocalDate end = ymd(matcher.group(2));
            if (start != null && end != null) {
                ranges.add(new LocalDate[]{start, end});
            }
        }
        return ranges;
    }

    private static LocalDate ymd(String value) {
        try {
            return LocalDate.of(Integer.parseInt(value.substring(0, 4)), Integer.parseInt(value.substring(4, 6)),
                    Integer.parseInt(value.substring(6, 8)));
        } catch (DateTimeException e) {
            return null;
        }
    }

    // 일이 없으면 시작은 1일, 종료는 말일
    private static LocalDate firstDate(String text, boolean endOfPeriod) {
        Matcher matcher = DATE.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            YearMonth month = YearMonth.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
            if (matcher.group(3) != null) {
                return month.atDay(Integer.parseInt(matcher.group(3)));
            }
            return endOfPeriod ? month.atEndOfMonth() : month.atDay(1);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static LocalDate monthOnlyEnd(String text, int year) {
        Matcher matcher = MONTH_ONLY.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return YearMonth.of(year, Integer.parseInt(matcher.group(1))).atEndOfMonth();
        } catch (DateTimeException e) {
            return null;
        }
    }
}
