package kr.fintarget.api.domain.policy.service;

import kr.fintarget.api.domain.policy.client.YouthCenterApiClient;
import kr.fintarget.api.domain.policy.client.dto.YouthCenterPolicyItem;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.mapper.YouthCenterPolicyMapper;
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
class YouthCenterPolicySyncServiceTest {

    @Mock
    private YouthCenterApiClient youthCenterApiClient;

    @Mock
    private PolicyRepository policyRepository;

    @InjectMocks
    private YouthCenterPolicySyncService youthCenterPolicySyncService;

    private YouthCenterPolicyItem itemWith(String plcyNo) {
        return new YouthCenterPolicyItem(
                plcyNo, "정책명", "키워드", "설명", "주거", "중분류",
                "지원내용", "기관코드", "기관명", "19", "39", "N",
                "20260101 ~ 20261231", "https://example.com", null,
                "2026-01-01 00:00:00", "2026-01-02 00:00:00"
        );
    }

    @Test
    void 이미_저장된_externalId와_source면_저장하지_않는다() {
        YouthCenterPolicyItem item = itemWith("EXISTING-1");
        when(youthCenterApiClient.fetchHousingAndFinancePolicies(1, 10)).thenReturn(List.of(item));
        when(policyRepository.existsByExternalIdAndSource("EXISTING-1", YouthCenterPolicyMapper.SOURCE)).thenReturn(true);

        int savedCount = youthCenterPolicySyncService.sync(1, 10);

        assertThat(savedCount).isZero();
        verify(policyRepository, never()).save(any(Policy.class));
    }

    @Test
    void 신규_정책은_저장한다() {
        YouthCenterPolicyItem item = itemWith("NEW-1");
        when(youthCenterApiClient.fetchHousingAndFinancePolicies(1, 10)).thenReturn(List.of(item));
        when(policyRepository.existsByExternalIdAndSource("NEW-1", YouthCenterPolicyMapper.SOURCE)).thenReturn(false);

        int savedCount = youthCenterPolicySyncService.sync(1, 10);

        assertThat(savedCount).isEqualTo(1);
        verify(policyRepository).save(any(Policy.class));
    }
}
