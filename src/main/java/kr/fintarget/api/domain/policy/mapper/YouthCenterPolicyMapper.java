package kr.fintarget.api.domain.policy.mapper;

import kr.fintarget.api.domain.policy.client.dto.YouthCenterPolicyItem;
import kr.fintarget.api.domain.policy.entity.Policy;

public class YouthCenterPolicyMapper {

    public static final String SOURCE = "YOUTH_CENTER";

    private YouthCenterPolicyMapper() {
    }

    public static Policy toPolicy(YouthCenterPolicyItem item) {
        return Policy.create(
                item.plcyNm(),
                item.plcyExplnCn(),
                parseAge(item.sprtTrgtMinAge()),
                parseAge(item.sprtTrgtMaxAge()),
                null, // 온통청년 응답에 정형화된 소득 상한 필드가 확인되지 않음
                null, // 온통청년 응답에 정형화된 지원금액 필드가 확인되지 않음 (plcySprtCn은 자유 텍스트)
                item.lclsfNm(),
                item.sprvsnInstCdNm(), // TODO: region의 의미가 "신청 가능 지역"이라면 zipCd(법정동코드) 기반으로 재매핑 필요.
                                       // 법정동코드→지역명 변환 테이블이 아직 없어 보류, sprvsnInstCdNm(운영기관명)으로 임시 매핑.
                item.plcyNo(),
                SOURCE
        );
    }

    private static Integer parseAge(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
