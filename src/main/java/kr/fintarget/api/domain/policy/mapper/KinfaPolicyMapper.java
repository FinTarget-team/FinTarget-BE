package kr.fintarget.api.domain.policy.mapper;

import kr.fintarget.api.domain.policy.client.dto.KinfaItem;
import kr.fintarget.api.domain.policy.entity.Policy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KinfaPolicyMapper {

    public static final String SOURCE = "KINFA";

    // snq는 basYm(기준연월) 내에서만 유효한 순번이라 그 자체로는 전역 고유값이 아님 -> basYm과 조합해 externalId 구성
    private static final Pattern MANWON_PATTERN = Pattern.compile("([\\d,]+)\\s*만원");

    private KinfaPolicyMapper() {
    }

    public static Policy toPolicy(KinfaItem item) {
        return Policy.create(
                item.finPrdNm(),
                item.suprTgtDtlCond(),
                null, // age 필드가 관측된 값("없음") 외의 형식이 검증되지 않아 파싱하지 않음
                null, // age 필드가 관측된 값("없음") 외의 형식이 검증되지 않아 파싱하지 않음
                null, // incm 필드가 관측된 값("없음") 외의 형식이 검증되지 않아 파싱하지 않음
                parseManwon(item.lnLmt()),
                item.usge(),
                item.rsdAreaPamtEqltIstm(),
                toExternalId(item),
                SOURCE
        );
    }

    public static String toExternalId(KinfaItem item) {
        return item.basYm() + "-" + item.snq();
    }

    private static Long parseManwon(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = MANWON_PATTERN.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        try {
            long manwon = Long.parseLong(matcher.group(1).replace(",", ""));
            return manwon * 10_000;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
