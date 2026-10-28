package kr.fintarget.api.domain.policy.mapper;

import kr.fintarget.api.domain.policy.client.dto.YouthCenterPolicyItem;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.user.code.Region;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class YouthCenterPolicyMapper {

    public static final String SOURCE = "YOUTH_CENTER";

    // zipCd(법정동코드) 앞 2자리 시/도 prefix → Region 변환표.
    // 근거 확인됨: 30 대전, 41 경기, 43 충북, 47 경북, 52 전북
    // 코드표 확인 필요(추정): 그 외 전부(아래 개별 주석 참고)
    // "12"(전남광주, 구 통합 코드)는 Region 1개로 매핑되지 않아 이 표에 넣지 않고 resolveRegion에서 별도 분기 처리
    private static final Map<String, Region> PREFIX_TO_REGION = new LinkedHashMap<>();

    static {
        PREFIX_TO_REGION.put("11", Region.SEOUL); // 코드표 확인 필요
        PREFIX_TO_REGION.put("26", Region.BUSAN); // 코드표 확인 필요
        PREFIX_TO_REGION.put("27", Region.DAEGU); // 코드표 확인 필요
        PREFIX_TO_REGION.put("28", Region.INCHEON); // 코드표 확인 필요
        PREFIX_TO_REGION.put("29", Region.GWANGJU); // 구 코드, 코드표 확인 필요
        PREFIX_TO_REGION.put("30", Region.DAEJEON); // 근거 확인됨
        PREFIX_TO_REGION.put("31", Region.ULSAN); // 코드표 확인 필요
        PREFIX_TO_REGION.put("36", Region.SEJONG); // 코드표 확인 필요
        PREFIX_TO_REGION.put("41", Region.GYEONGGI); // 근거 확인됨
        PREFIX_TO_REGION.put("42", Region.GANGWON); // 구 코드, 코드표 확인 필요
        PREFIX_TO_REGION.put("43", Region.CHUNGBUK); // 근거 확인됨
        PREFIX_TO_REGION.put("44", Region.CHUNGNAM); // 코드표 확인 필요
        PREFIX_TO_REGION.put("45", Region.JEONBUK); // 구 코드, 코드표 확인 필요
        PREFIX_TO_REGION.put("46", Region.JEONNAM); // 구 코드, 코드표 확인 필요
        PREFIX_TO_REGION.put("47", Region.GYEONGBUK); // 근거 확인됨
        PREFIX_TO_REGION.put("48", Region.GYEONGNAM); // 코드표 확인 필요
        PREFIX_TO_REGION.put("50", Region.JEJU); // 코드표 확인 필요
        PREFIX_TO_REGION.put("51", Region.GANGWON); // 코드표 확인 필요
        PREFIX_TO_REGION.put("52", Region.JEONBUK); // 근거 확인됨
    }

    private YouthCenterPolicyMapper() {
    }

    public static Policy toPolicy(YouthCenterPolicyItem item) {
        return Policy.create(
                item.plcyNm(),
                item.plcyExplnCn(),
                parseAge(item.sprtTrgtMinAge(), item.plcyNo(), "sprtTrgtMinAge"),
                parseAge(item.sprtTrgtMaxAge(), item.plcyNo(), "sprtTrgtMaxAge"),
                null, // 온통청년 응답에 정형화된 소득 상한 필드가 확인되지 않음
                null, // 온통청년 응답에 정형화된 지원금액 필드가 확인되지 않음 (plcySprtCn은 자유 텍스트)
                resolvePolicyType(item.lclsfNm()),
                resolveRegion(item.zipCd()),
                item.plcyNo(),
                SOURCE
        );
    }

    /**
     * 온통청년 zipCd(신청 가능 지역의 법정동코드, 쉼표로 구분) 값을 Region 코드(User.region과
     * 동일한 17개 시/도 코드)로 변환한다. 저장되는 문자열의 토큰 순서는 zipCd에 등장한 순서를
     * 그대로 따르므로, 이 값으로 필터링할 때는 전체 문자열을 그대로 비교하지 말고 쉼표로 분리한
     * 코드 단위로 비교해야 한다.
     * - null/빈 문자열: null
     * - 쉼표로 구분된 각 토큰의 앞 2자리를 시/도 prefix로 추출, 중복 제거(입력 순서 유지)
     * - "12"(구 전남광주 통합 코드)는 GWANGJU, JEONNAM 두 Region으로 전개
     * - 매핑된 Region 집합이 17개 시/도 전체를 포함하면 전국으로 보고 null
     * - 매핑되지 않는 prefix는 해당 토큰만 제외하고 WARN 로그, 전부 매핑 실패 시 null
     */
    private static String resolveRegion(String zipCd) {
        if (zipCd == null || zipCd.isBlank()) {
            return null;
        }

        Set<Region> regions = new LinkedHashSet<>();
        for (String token : zipCd.split(",")) {
            String trimmed = token.trim();
            if (trimmed.length() < 2) {
                log.warn("온통청년 zipCd 토큰 형식이 올바르지 않음: {} (전체 값: {})", trimmed, zipCd);
                continue;
            }
            String prefix = trimmed.substring(0, 2);
            if ("12".equals(prefix)) {
                regions.add(Region.GWANGJU);
                regions.add(Region.JEONNAM);
                continue;
            }
            Region region = PREFIX_TO_REGION.get(prefix);
            if (region == null) {
                log.warn("온통청년 zipCd 중 매핑되지 않는 시/도 prefix: {} (전체 값: {})", prefix, zipCd);
                continue;
            }
            regions.add(region);
        }

        if (regions.isEmpty()) {
            return null;
        }
        // Region에 상수가 추가되면 Region.values().length가 늘어나므로 이 전국 판정도 함께
        // 깨진다(새 상수가 포함된 전국 목록만 전국으로 인식되지 않게 됨) — Region 상수 추가 시 반드시 재검토할 것.
        if (regions.size() == Region.values().length) {
            return null;
        }
        return regions.stream().map(Region::getCode).collect(Collectors.joining(","));
    }

    private static PolicyType resolvePolicyType(String lclsfNm) {
        if (lclsfNm == null) {
            return PolicyType.ETC;
        }
        if (lclsfNm.contains("주거")) {
            return PolicyType.HOUSING;
        }
        if (lclsfNm.contains("금융")) {
            return PolicyType.FINANCE_WELFARE;
        }
        return PolicyType.ETC;
    }

    /**
     * 온통청년 지원대상 최소/최대 연령을 Policy 나이 조건으로 변환한다. null은 "제한 없음"이다.
     * - null/빈 문자열: null
     * - 0 이하: null. 온통청년은 나이 제한이 없는 정책에 0을 보내며, 최소·최대를 각각 판정한다 (예: 18, 0 → 18 이상 상한 없음)
     * - 숫자가 아님: null, 정책 ID와 원래 값을 WARN 로그로 남김
     * - 99, 120처럼 큰 상한은 사용자 나이로 넘을 수 없어 그대로 둔다
     * sprtTrgtAgeLmtYn(나이 제한 여부)은 읽지 않는다. 2026-10 실응답 기준 Y인데 19~39처럼 실제 나이가 있는 정책과
     * N인데 0/0인 정책이 섞여 있어 판정 근거로 쓸 수 없다 (#110).
     */
    private static Integer parseAge(String value, String plcyNo, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        int age;
        try {
            age = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("온통청년 나이 값이 숫자가 아니어서 제한 없음으로 저장: plcyNo={}, {}={}", plcyNo, field, value);
            return null;
        }
        return age <= 0 ? null : age;
    }
}
