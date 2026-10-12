package kr.fintarget.api.domain.policy.mapper;

import kr.fintarget.api.domain.policy.client.dto.KinfaItem;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KinfaPolicyMapper {

    public static final String SOURCE = "KINFA";

    // snq는 기준연월(basYm)마다 다시 매겨지는 순번이라 같은 snq가 다른 상품을 가리킬 수 있고,
    // basYm을 붙이면 같은 상품이 달마다 중복 저장된다. 월 안에서 고유하고 월이 바뀌어도 유지되는
    // 제공기관명 + 상품명 조합을 상품 식별자로 쓴다 (2026-10 실데이터 기준 검증).
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
                resolvePolicyType(item.usge()),
                item.rsdAreaPamtEqltIstm(),
                toExternalId(item),
                SOURCE
        );
    }

    public static String toExternalId(KinfaItem item) {
        return item.ofrInstNm() + "|" + item.finPrdNm();
    }

    private static PolicyType resolvePolicyType(String usge) {
        if (usge != null && usge.contains("창업")) {
            return PolicyType.STARTUP;
        }
        return PolicyType.FINANCE_WELFARE; // 서민금융 상품은 대부분 금융복지 성격
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
