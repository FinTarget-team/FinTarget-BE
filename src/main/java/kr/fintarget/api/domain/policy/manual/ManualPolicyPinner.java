package kr.fintarget.api.domain.policy.manual;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.manual.ManualPolicyCatalog.Pin;
import kr.fintarget.api.domain.user.code.EmploymentType;
import kr.fintarget.api.domain.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 리소스의 고정 노출 대상(pins)을 직업 유형별로 추천 목록 맨 앞에 고정 노출한다 (#245).
 *
 * <p>직접 등록 정책과 신청 기간을 보정한 서울 청년 월세 지원은 모집 종료·신청 기간 판정 불가로 규칙 정렬 맨 뒤에 있어
 * 추천 후보에 들어오지 못한다. 규칙 정렬·리랭킹·캐시는 바꾸지 않고, 최종 결과(리랭킹 결과든 규칙 대체 결과든)를 응답하기 직전에 덧입힌다.
 *
 * <ul>
 *   <li>사용자 직업 유형이 대상의 recommendFor에 있고, 그 정책이 이 사용자의 공통 후보 조회(나이·소득·지역 등)를 통과했을 때만 넣는다</li>
 *   <li>출처·외부 정책 ID·정책명이 모두 같은 정책만 넣는다 (다른 환경에서 정책번호가 다르면 고정하지 않음)</li>
 *   <li>고정 정책을 리소스 순서대로 1위부터 넣고, 원래 결과는 그 뒤에 원래 순서대로 이어 붙인다 (이미 결과에 있으면 고정 자리로 옮김)</li>
 *   <li>최대 N건(policy.recommend.limit)을 지키므로 원래 결과의 하위 정책이 밀려난다</li>
 * </ul>
 * policy.recommend.manual-pin.enabled=false면 결과를 그대로 돌려준다.
 */
@Slf4j
@Component
public class ManualPolicyPinner {

    private final ManualPolicyCatalog catalog;
    private final boolean enabled;
    private final int limit;

    @Autowired
    public ManualPolicyPinner(ManualPolicyCatalog catalog,
                              @Value("${policy.recommend.manual-pin.enabled:true}") boolean enabled,
                              @Value("${policy.recommend.limit:10}") int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("policy.recommend.limit는 1 이상이어야 합니다: " + limit);
        }
        this.catalog = catalog;
        this.enabled = enabled;
        this.limit = limit;
    }

    /** 고정 노출을 하지 않는 인스턴스 (테스트용) */
    public static ManualPolicyPinner disabled() {
        return new ManualPolicyPinner(new ManualPolicyCatalog(List.of()), false, 10);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param candidates 이 사용자의 공통 후보 조회 결과 (고정 정책이 여기 있어야 넣는다)
     * @param selected   최종 추천 결과 (리랭킹 또는 규칙)
     */
    public List<Policy> pin(User user, List<Policy> candidates, List<Policy> selected) {
        if (!enabled) {
            return selected;
        }
        List<Policy> pins = pinnedFor(user, candidates);
        if (pins.isEmpty()) {
            return selected;
        }
        Map<UUID, Policy> result = new LinkedHashMap<>();
        for (Policy policy : pins) {
            result.putIfAbsent(policy.getPolicyId(), policy);
        }
        for (Policy policy : selected) {
            if (result.size() >= limit) {
                break;
            }
            result.putIfAbsent(policy.getPolicyId(), policy);
        }
        List<Policy> pinned = new ArrayList<>(result.values());
        return pinned.size() > limit ? List.copyOf(pinned.subList(0, limit)) : List.copyOf(pinned);
    }

    /** 이 사용자에게 고정 노출할 정책 (리소스 순서) */
    List<Policy> pinnedFor(User user, List<Policy> candidates) {
        EmploymentType employment = EmploymentType.fromCode(user.getEmploymentType());
        if (employment == null) {
            return List.of();
        }
        List<Pin> targets = catalog.pins().stream().filter(pin -> pin.recommendedFor(employment)).toList();
        if (targets.isEmpty()) {
            return List.of();
        }
        Map<String, Policy> byKey = new LinkedHashMap<>();
        for (Policy policy : candidates) {
            if (policy.getSource() != null && policy.getExternalId() != null) {
                byKey.putIfAbsent(policy.getSource() + "|" + policy.getExternalId(), policy);
            }
        }
        List<Policy> pins = new ArrayList<>();
        for (Pin pin : targets) {
            Policy policy = byKey.get(pin.source() + "|" + pin.externalId());
            if (pin.matches(policy)) {
                pins.add(policy);
            }
        }
        return pins;
    }
}
