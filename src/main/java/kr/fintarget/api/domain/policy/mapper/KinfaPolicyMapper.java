package kr.fintarget.api.domain.policy.mapper;

import kr.fintarget.api.domain.policy.client.dto.KinfaItem;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.user.code.Region;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class KinfaPolicyMapper {

    public static final String SOURCE = "KINFA";

    // snq는 기준연월(basYm)마다 다시 매겨지는 순번이라 같은 snq가 다른 상품을 가리킬 수 있고,
    // basYm을 붙이면 같은 상품이 달마다 중복 저장된다. 월 안에서 고유하고 월이 바뀌어도 유지되는
    // 제공기관명 + 상품명 조합을 상품 식별자로 쓴다 (2026-10 실데이터 기준 검증).
    private static final Pattern MANWON_PATTERN = Pattern.compile("([\\d,]+)\\s*만원");

    // KINFA rsdAreaPamtEqltIstm 값 중 "지역 제한 없음"을 뜻하는 표현들
    private static final Set<String> NATIONWIDE_VALUES = Set.of("전국", "전국(농어촌)");

    // rsdAreaPamtEqltIstm이 "-"일 때 정책명(name)에서 시도 정식명칭을 찾아 보정하기 위한 테이블.
    // 약칭("경기", "서울")이 아닌 정식명칭만 사용 — 약칭으로 정책명을 검색하면 "경기침체" 등 오탐이 발생함.
    // "강원특별자치도"/"강원도", "전북특별자치도"/"전라북도"는 명칭 변경 전후 표기가 혼재할 수 있어 둘 다 매핑.
    private static final Map<String, Region> FULL_NAME_TO_REGION = new LinkedHashMap<>();

    static {
        FULL_NAME_TO_REGION.put("서울특별시", Region.SEOUL);
        FULL_NAME_TO_REGION.put("부산광역시", Region.BUSAN);
        FULL_NAME_TO_REGION.put("대구광역시", Region.DAEGU);
        FULL_NAME_TO_REGION.put("인천광역시", Region.INCHEON);
        FULL_NAME_TO_REGION.put("광주광역시", Region.GWANGJU);
        FULL_NAME_TO_REGION.put("대전광역시", Region.DAEJEON);
        FULL_NAME_TO_REGION.put("울산광역시", Region.ULSAN);
        FULL_NAME_TO_REGION.put("세종특별자치시", Region.SEJONG);
        FULL_NAME_TO_REGION.put("경기도", Region.GYEONGGI);
        FULL_NAME_TO_REGION.put("강원특별자치도", Region.GANGWON);
        FULL_NAME_TO_REGION.put("강원도", Region.GANGWON);
        FULL_NAME_TO_REGION.put("충청북도", Region.CHUNGBUK);
        FULL_NAME_TO_REGION.put("충청남도", Region.CHUNGNAM);
        FULL_NAME_TO_REGION.put("전북특별자치도", Region.JEONBUK);
        FULL_NAME_TO_REGION.put("전라북도", Region.JEONBUK);
        FULL_NAME_TO_REGION.put("전라남도", Region.JEONNAM);
        FULL_NAME_TO_REGION.put("경상북도", Region.GYEONGBUK);
        FULL_NAME_TO_REGION.put("경상남도", Region.GYEONGNAM);
        FULL_NAME_TO_REGION.put("제주특별자치도", Region.JEJU);
    }

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
                resolveRegion(item.rsdAreaPamtEqltIstm(), item.finPrdNm()),
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

    /**
     * KINFA rsdAreaPamtEqltIstm 값을 Region 코드(User.region과 동일한 17개 시/도 코드)로 변환한다.
     * - null/빈 문자열, "전국"/"전국(농어촌)": null (지역 제한 없음)
     * - "-": 정책명에서 시도 정식명칭을 찾아 보정, 없으면 null
     * - 쉼표로 구분된 다중 지역: 각각 약칭→코드 변환 후 쉼표로 재연결 (중복 제거, 입력 순서 유지)
     * - 단일 약칭: 코드로 변환
     * - 매핑되지 않는 값: null + WARN 로그
     */
    private static String resolveRegion(String rawRegion, String policyName) {
        if (rawRegion == null || rawRegion.isBlank()) {
            return null;
        }
        String trimmed = rawRegion.trim();
        if (NATIONWIDE_VALUES.contains(trimmed)) {
            return null;
        }
        if ("-".equals(trimmed)) {
            return resolveFromPolicyName(policyName);
        }
        if (trimmed.contains(",")) {
            return resolveMultiple(trimmed);
        }
        Region region = Region.fromLabel(trimmed);
        if (region == null) {
            log.warn("KINFA 지역 값이 Region 코드로 매핑되지 않음: {}", trimmed);
            return null;
        }
        return region.getCode();
    }

    private static String resolveMultiple(String value) {
        List<String> codes = new ArrayList<>();
        for (String token : value.split(",")) {
            String trimmedToken = token.trim();
            Region region = Region.fromLabel(trimmedToken);
            if (region == null) {
                log.warn("KINFA 다중 지역 중 매핑되지 않는 토큰: {} (전체 값: {})", trimmedToken, value);
                continue;
            }
            if (!codes.contains(region.getCode())) {
                codes.add(region.getCode());
            }
        }
        return codes.isEmpty() ? null : String.join(",", codes);
    }

    private static String resolveFromPolicyName(String policyName) {
        if (policyName == null) {
            return null;
        }

        Map<Integer, Region> foundByPosition = new LinkedHashMap<>();
        for (Map.Entry<String, Region> entry : FULL_NAME_TO_REGION.entrySet()) {
            int index = policyName.indexOf(entry.getKey());
            if (index >= 0 && !foundByPosition.containsValue(entry.getValue())) {
                foundByPosition.put(index, entry.getValue());
            }
        }

        if (foundByPosition.isEmpty()) {
            return null;
        }

        List<Integer> sortedPositions = new ArrayList<>(foundByPosition.keySet());
        sortedPositions.sort(Integer::compareTo);

        List<String> codes = new ArrayList<>();
        for (Integer position : sortedPositions) {
            codes.add(foundByPosition.get(position).getCode());
        }
        return String.join(",", codes);
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
