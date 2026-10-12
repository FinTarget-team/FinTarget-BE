package kr.fintarget.api.domain.policy.service;

import kr.fintarget.api.domain.policy.client.KinfaApiClient;
import kr.fintarget.api.domain.policy.client.dto.KinfaItem;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.mapper.KinfaPolicyMapper;
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
    private final PolicySyncWriter policySyncWriter;

    @Transactional
    public int sync() {
        List<KinfaItem> items = kinfaApiClient.fetchLatestActiveProducts();

        List<Policy> policies = items.stream()
                .filter(item -> item.ofrInstNm() != null && item.finPrdNm() != null)
                .map(KinfaPolicyMapper::toPolicy)
                .toList();
        PolicySyncWriter.SyncCounts counts = policySyncWriter.apply(KinfaPolicyMapper.SOURCE, policies);

        log.info("KINFA 서민금융상품 동기화 완료: 조회 {}건 - 신규 {}건, 갱신 {}건, 삭제 {}건",
                items.size(), counts.saved(), counts.updated(), counts.deleted());
        return counts.saved();
    }
}
