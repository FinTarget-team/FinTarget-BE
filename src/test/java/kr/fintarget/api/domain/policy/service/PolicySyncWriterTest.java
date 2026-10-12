package kr.fintarget.api.domain.policy.service;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.policy.repository.UserPolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicySyncWriterTest {

    private static final String SOURCE = "TEST";

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private UserPolicyRepository userPolicyRepository;

    @InjectMocks
    private PolicySyncWriter policySyncWriter;

    private Policy policy(String externalId, String name) {
        return Policy.create(name, "설명", null, null, null, null, PolicyType.HOUSING, "전국", externalId, SOURCE);
    }

    private Policy stored(String externalId, String name) {
        Policy policy = policy(externalId, name);
        ReflectionTestUtils.setField(policy, "policyId", UUID.randomUUID());
        return policy;
    }

    @Test
    void 없는_정책은_추가하고_있는_정책은_최신_내용으로_갱신한다() {
        Policy existing = stored("A", "예전 이름");
        when(policyRepository.findByExternalIdAndSource("A", SOURCE)).thenReturn(Optional.of(existing));
        when(policyRepository.findByExternalIdAndSource("B", SOURCE)).thenReturn(Optional.empty());
        when(policyRepository.findBySource(SOURCE)).thenReturn(List.of(existing));

        PolicySyncWriter.SyncCounts counts = policySyncWriter.apply(SOURCE, List.of(policy("A", "새 이름"), policy("B", "신규")));

        assertThat(counts).isEqualTo(new PolicySyncWriter.SyncCounts(1, 1, 0));
        assertThat(existing.getName()).isEqualTo("새 이름");
        verify(policyRepository).save(any(Policy.class));
    }

    @Test
    void 목록에서_빠진_정책은_삭제하되_사용자가_신청한_정책은_남긴다() {
        Policy kept = stored("A", "유지");
        Policy ended = stored("OLD-1", "종료됨");
        Policy applied = stored("OLD-2", "신청자 있음");
        when(policyRepository.findByExternalIdAndSource("A", SOURCE)).thenReturn(Optional.of(kept));
        when(policyRepository.findBySource(SOURCE)).thenReturn(List.of(kept, ended, applied));
        when(userPolicyRepository.existsByPolicyPolicyId(ended.getPolicyId())).thenReturn(false);
        when(userPolicyRepository.existsByPolicyPolicyId(applied.getPolicyId())).thenReturn(true);

        PolicySyncWriter.SyncCounts counts = policySyncWriter.apply(SOURCE, List.of(policy("A", "유지")));

        assertThat(counts.deleted()).isEqualTo(1);
        verify(policyRepository).delete(ended);
        verify(policyRepository, never()).delete(applied);
        verify(policyRepository, never()).delete(kept);
    }

    @Test
    void 받아온_목록이_비어_있으면_기존_정책을_지우지_않는다() {
        PolicySyncWriter.SyncCounts counts = policySyncWriter.apply(SOURCE, List.of());

        assertThat(counts).isEqualTo(new PolicySyncWriter.SyncCounts(0, 0, 0));
        verify(policyRepository, never()).findBySource(any());
        verify(policyRepository, never()).delete(any());
    }

    @Test
    void 같은_externalId가_여러_번_오면_한_번만_반영한다() {
        when(policyRepository.findByExternalIdAndSource("A", SOURCE)).thenReturn(Optional.empty());
        when(policyRepository.findBySource(SOURCE)).thenReturn(List.of());

        PolicySyncWriter.SyncCounts counts = policySyncWriter.apply(SOURCE, List.of(policy("A", "1"), policy("A", "2")));

        assertThat(counts.saved()).isEqualTo(1);
    }
}
