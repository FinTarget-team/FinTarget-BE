package kr.fintarget.api.domain.user.code;

import java.util.Arrays;

/**
 * User.region에 허용되는 대한민국 17개 시/도 코드.
 * Policy.region은 아직 표준화된 지역 포맷을 갖고 있지 않아 이 코드와 매칭되지 않음(PolicyMapper 참고).
 * 정책 매칭용 값(예: 전국 단위)은 이 enum에 포함하지 않음 — User.region 전용.
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
