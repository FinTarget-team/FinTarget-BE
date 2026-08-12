package kr.fintarget.api.domain.policy.service;

import kr.fintarget.api.domain.policy.client.YouthCenterApiClient;
import kr.fintarget.api.domain.policy.client.dto.YouthCenterPolicyItem;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.mapper.YouthCenterPolicyMapper;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouthCenterPolicySyncService {

    private final YouthCenterApiClient youthCenterApiClient;
    private final PolicyRepository policyRepository;

    @Transactional
    public int sync(int pageIndex, int display) {
        List<YouthCenterPolicyItem> items = youthCenterApiClient.fetchHousingAndFinancePolicies(pageIndex, display);

        int savedCount = 0;
        for (YouthCenterPolicyItem item : items) {
            if (item.plcyNo() == null || item.plcyNo().isBlank()) {
                continue;
            }
            if (policyRepository.existsByExternalIdAndSource(item.plcyNo(), YouthCenterPolicyMapper.SOURCE)) {
                continue;
            }

            Policy policy = YouthCenterPolicyMapper.toPolicy(item);
            policyRepository.save(policy);
            savedCount++;
        }

        log.info("온통청년 정책 동기화 완료: 조회 {}건 중 {}건 신규 저장", items.size(), savedCount);
        return savedCount;
    }
}
