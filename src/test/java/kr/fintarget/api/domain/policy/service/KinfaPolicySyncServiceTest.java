package kr.fintarget.api.domain.policy.service;

import kr.fintarget.api.domain.policy.client.KinfaApiClient;
import kr.fintarget.api.domain.policy.client.dto.KinfaItem;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.mapper.KinfaPolicyMapper;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KinfaPolicySyncServiceTest {

    @Mock
    private KinfaApiClient kinfaApiClient;

    @Mock
    private PolicyRepository policyRepository;

    @InjectMocks
    private KinfaPolicySyncService kinfaPolicySyncService;

    private KinfaItem itemWith(String basYm, String snq) {
        return new KinfaItem(
                basYm, snq, "상품명", "3500만원", "변동금리", "은행별 상이", "은행별 상이", "-", "-",
                "원(리)금균등분할상환", "생계", "근로자", "시중은행", "14개 취급은행", "전국",
                "지원조건", "없음", "없음", "은행 문의", "Y", "1", "대출상품"
        );
    }

    @Test
    void 이미_저장된_externalId와_source면_저장하지_않는다() {
        KinfaItem item = itemWith("202607", "1");
        when(kinfaApiClient.fetchActiveProducts(1, 10)).thenReturn(List.of(item));
        when(policyRepository.existsByExternalIdAndSource("202607-1", KinfaPolicyMapper.SOURCE)).thenReturn(true);

        int savedCount = kinfaPolicySyncService.sync(1, 10);

        assertThat(savedCount).isZero();
        verify(policyRepository, never()).save(any(Policy.class));
    }

    @Test
    void 신규_상품은_저장한다() {
        KinfaItem item = itemWith("202607", "2");
        when(kinfaApiClient.fetchActiveProducts(1, 10)).thenReturn(List.of(item));
        when(policyRepository.existsByExternalIdAndSource("202607-2", KinfaPolicyMapper.SOURCE)).thenReturn(false);

        int savedCount = kinfaPolicySyncService.sync(1, 10);

        assertThat(savedCount).isEqualTo(1);
        verify(policyRepository).save(any(Policy.class));
    }
}
