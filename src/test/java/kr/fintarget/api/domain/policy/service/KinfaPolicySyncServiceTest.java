package kr.fintarget.api.domain.policy.service;

import kr.fintarget.api.domain.policy.client.KinfaApiClient;
import kr.fintarget.api.domain.policy.client.dto.KinfaItem;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.mapper.KinfaPolicyMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KinfaPolicySyncServiceTest {

    @Mock
    private KinfaApiClient kinfaApiClient;

    @Mock
    private PolicySyncWriter policySyncWriter;

    @InjectMocks
    private KinfaPolicySyncService kinfaPolicySyncService;

    private KinfaItem itemWith(String ofrInstNm, String finPrdNm) {
        return new KinfaItem(
                "202609", "1", finPrdNm, "3500만원", "변동금리", "은행별 상이", "은행별 상이", "-", "-",
                "원(리)금균등분할상환", "생계", "근로자", "시중은행", ofrInstNm, "전국",
                "지원조건", "없음", "없음", "은행 문의", "Y", "1", "대출상품", null, null, null, null, null
        );
    }

    @Test
    void 최신_기준연월_상품을_매핑해_writer에_넘기고_신규_건수를_반환한다() {
        when(kinfaApiClient.fetchLatestActiveProducts())
                .thenReturn(List.of(itemWith("14개 취급은행", "새희망홀씨Ⅱ"), itemWith("서민금융진흥원", "햇살론")));
        when(policySyncWriter.apply(eq(KinfaPolicyMapper.SOURCE), anyList()))
                .thenReturn(new PolicySyncWriter.SyncCounts(2, 0, 0));

        int savedCount = kinfaPolicySyncService.sync();

        assertThat(savedCount).isEqualTo(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Policy>> captor = ArgumentCaptor.forClass(List.class);
        verify(policySyncWriter).apply(eq(KinfaPolicyMapper.SOURCE), captor.capture());
        assertThat(captor.getValue()).extracting(Policy::getExternalId)
                .containsExactly("14개 취급은행|새희망홀씨Ⅱ", "서민금융진흥원|햇살론");
    }

    @Test
    void 제공기관명이나_상품명이_없는_항목은_제외한다() {
        when(kinfaApiClient.fetchLatestActiveProducts())
                .thenReturn(List.of(itemWith(null, "상품"), itemWith("기관", null), itemWith("기관", "상품")));
        when(policySyncWriter.apply(eq(KinfaPolicyMapper.SOURCE), anyList()))
                .thenReturn(new PolicySyncWriter.SyncCounts(1, 0, 0));

        kinfaPolicySyncService.sync();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Policy>> captor = ArgumentCaptor.forClass(List.class);
        verify(policySyncWriter).apply(eq(KinfaPolicyMapper.SOURCE), captor.capture());
        assertThat(captor.getValue()).extracting(Policy::getExternalId).containsExactly("기관|상품");
    }
}
