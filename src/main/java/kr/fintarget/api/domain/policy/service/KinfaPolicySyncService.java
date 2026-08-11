package kr.fintarget.api.domain.policy.service;

import kr.fintarget.api.domain.policy.client.KinfaApiClient;
import kr.fintarget.api.domain.policy.client.dto.KinfaItem;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.mapper.KinfaPolicyMapper;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KinfaPolicySyncService {

    private final KinfaApiClient kinfaApiClient;
    private final PolicyRepository policyRepository;

    @Transactional
    public int sync(int pageNo, int numOfRows) {
        List<KinfaItem> items = kinfaApiClient.fetchActiveProducts(pageNo, numOfRows);

        int savedCount = 0;
        for (KinfaItem item : items) {
            if (item.basYm() == null || item.snq() == null) {
                continue;
            }
            String externalId = KinfaPolicyMapper.toExternalId(item);
            if (policyRepository.existsByExternalIdAndSource(externalId, KinfaPolicyMapper.SOURCE)) {
                continue;
            }

            Policy policy = KinfaPolicyMapper.toPolicy(item);
            policyRepository.save(policy);
            savedCount++;
        }

        log.info("KINFA 서민금융상품 동기화 완료: 조회 {}건 중 {}건 신규 저장", items.size(), savedCount);
        return savedCount;
    }
}
