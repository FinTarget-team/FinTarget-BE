package kr.fintarget.api.domain.policy.service;

import kr.fintarget.api.domain.policy.client.YouthCenterApiClient;
import kr.fintarget.api.domain.policy.client.dto.YouthCenterPolicyItem;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.mapper.YouthCenterPolicyMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YouthCenterPolicySyncServiceTest {

    @Mock
    private YouthCenterApiClient youthCenterApiClient;

    @Mock
    private PolicySyncWriter policySyncWriter;

    @InjectMocks
    private YouthCenterPolicySyncService youthCenterPolicySyncService;

    private YouthCenterPolicyItem itemWith(String plcyNo) {
        return new YouthCenterPolicyItem(
                plcyNo, "정책명", "키워드", "설명", "주거", "중분류",
                "지원내용", "기관코드", "기관명", "19", "39", "N",
                "20260101 ~ 20261231", "https://example.com", null,
                "2026-01-01 00:00:00", "2026-01-02 00:00:00", "0057001"
        );
    }

    @Test
    void 받아온_정책을_매핑해_writer에_넘기고_신규_건수를_반환한다() {
        when(youthCenterApiClient.fetchOpenHousingAndFinancePolicies(any()))
                .thenReturn(List.of(itemWith("P-1"), itemWith("P-2")));
        when(policySyncWriter.apply(eq(YouthCenterPolicyMapper.SOURCE), anyList()))
                .thenReturn(new PolicySyncWriter.SyncCounts(1, 1, 0));

        int savedCount = youthCenterPolicySyncService.sync();

        assertThat(savedCount).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Policy>> captor = ArgumentCaptor.forClass(List.class);
        verify(policySyncWriter).apply(eq(YouthCenterPolicyMapper.SOURCE), captor.capture());
        assertThat(captor.getValue()).extracting(Policy::getExternalId).containsExactly("P-1", "P-2");
    }

    @Test
    void 정책번호가_없는_항목은_제외한다() {
        when(youthCenterApiClient.fetchOpenHousingAndFinancePolicies(any()))
                .thenReturn(List.of(itemWith(" "), itemWith("P-1")));
        when(policySyncWriter.apply(eq(YouthCenterPolicyMapper.SOURCE), anyList()))
                .thenReturn(new PolicySyncWriter.SyncCounts(1, 0, 0));

        youthCenterPolicySyncService.sync();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Policy>> captor = ArgumentCaptor.forClass(List.class);
        verify(policySyncWriter).apply(eq(YouthCenterPolicyMapper.SOURCE), captor.capture());
        assertThat(captor.getValue()).extracting(Policy::getExternalId).containsExactly("P-1");
    }
}
