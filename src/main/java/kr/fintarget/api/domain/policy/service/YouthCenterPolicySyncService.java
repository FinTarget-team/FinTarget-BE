package kr.fintarget.api.domain.policy.service;

import kr.fintarget.api.domain.policy.client.YouthCenterApiClient;
import kr.fintarget.api.domain.policy.client.dto.YouthCenterPolicyItem;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.mapper.YouthCenterPolicyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouthCenterPolicySyncService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final YouthCenterApiClient youthCenterApiClient;
    private final PolicySyncWriter policySyncWriter;

    @Transactional
    public int sync() {
        List<YouthCenterPolicyItem> items =
                youthCenterApiClient.fetchOpenHousingAndFinancePolicies(LocalDate.now(SEOUL));

        List<Policy> policies = items.stream()
                .filter(item -> item.plcyNo() != null && !item.plcyNo().isBlank())
                .map(YouthCenterPolicyMapper::toPolicy)
                .toList();
        PolicySyncWriter.SyncCounts counts = policySyncWriter.apply(YouthCenterPolicyMapper.SOURCE, policies);

        log.info("온통청년 정책 동기화 완료: 조회 {}건 - 신규 {}건, 갱신 {}건, 삭제 {}건",
                items.size(), counts.saved(), counts.updated(), counts.deleted());
        return counts.saved();
    }
}
