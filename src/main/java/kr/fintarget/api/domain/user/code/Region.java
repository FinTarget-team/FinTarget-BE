package kr.fintarget.api.domain.user.code;

import java.util.Arrays;

/**
 * 대한민국 17개 시/도 코드. User.region에 허용되는 값이며, Policy.region도 이 코드를 쉼표로 이어 저장한다
 * (YouthCenterPolicyMapper, KinfaPolicyMapper가 변환. 전국 정책은 null).
 * 전국 단위 같은 정책 매칭용 값은 이 enum에 포함하지 않음 — 지역 일치 판정은 Policy.appliesToRegion 참고.
 */
public enum Region {
    SEOUL("SEOUL", "서울"),
    BUSAN("BUSAN", "부산"),
    DAEGU("DAEGU", "대구"),
    INCHEON("INCHEON", "인천"),
    GWANGJU("GWANGJU", "광주"),
    DAEJEON("DAEJEON", "대전"),
    ULSAN("ULSAN", "울산"),
    SEJONG("SEJONG", "세종"),
    GYEONGGI("GYEONGGI", "경기"),
    GANGWON("GANGWON", "강원"),
    CHUNGBUK("CHUNGBUK", "충북"),
    CHUNGNAM("CHUNGNAM", "충남"),
    JEONBUK("JEONBUK", "전북"),
    JEONNAM("JEONNAM", "전남"),
    GYEONGBUK("GYEONGBUK", "경북"),
    GYEONGNAM("GYEONGNAM", "경남"),
    JEJU("JEJU", "제주");

    private final String code;
    private final String label;

    Region(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static Region fromCode(String code) {
        return Arrays.stream(values())
                .filter(region -> region.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    public static Region fromLabel(String label) {
        if (label == null) {
            return null;
        }
        String trimmed = label.trim();
        return Arrays.stream(values())
                .filter(region -> region.label.equals(trimmed))
                .findFirst()
                .orElse(null);
    }
}
