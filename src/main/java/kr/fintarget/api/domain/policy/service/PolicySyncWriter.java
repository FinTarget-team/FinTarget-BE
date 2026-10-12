package kr.fintarget.api.domain.policy.service;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import kr.fintarget.api.domain.policy.repository.UserPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 외부 API에서 받아온 소스 전체 목록을 기준으로 정책 테이블을 맞춘다.
 * - 없는 정책은 추가, 이미 있는 정책은 최신 내용으로 갱신
 * - 목록에서 빠진(종료/판매중단) 정책은 삭제. 단, 사용자가 신청한(user_policy가 참조하는) 정책은 남긴다
 * 호출하는 쪽의 트랜잭션 안에서 실행되어야 한다.
 */
@Component
@RequiredArgsConstructor
public class PolicySyncWriter {

    private final PolicyRepository policyRepository;
    private final UserPolicyRepository userPolicyRepository;

    public SyncCounts apply(String source, List<Policy> fetchedPolicies) {
        Map<String, Policy> fetchedByExternalId = new LinkedHashMap<>();
        for (Policy policy : fetchedPolicies) {
            if (policy.getExternalId() != null && !policy.getExternalId().isBlank()) {
                fetchedByExternalId.put(policy.getExternalId(), policy);
            }
        }

        int saved = 0;
        int updated = 0;
        for (Policy fetched : fetchedByExternalId.values()) {
            Optional<Policy> existing = policyRepository.findByExternalIdAndSource(fetched.getExternalId(), source);
            if (existing.isPresent()) {
                existing.get().updateFrom(fetched);
                updated++;
            } else {
                policyRepository.save(fetched);
                saved++;
            }
        }

        // 받아온 목록이 비어 있으면 API 이상일 가능성이 커서 기존 데이터를 지우지 않는다
        int deleted = 0;
        if (!fetchedByExternalId.isEmpty()) {
            for (Policy stored : policyRepository.findBySource(source)) {
                if (!fetchedByExternalId.containsKey(stored.getExternalId())
                        && !userPolicyRepository.existsByPolicyPolicyId(stored.getPolicyId())) {
                    policyRepository.delete(stored);
                    deleted++;
                }
            }
        }

        return new SyncCounts(saved, updated, deleted);
    }

    public record SyncCounts(int saved, int updated, int deleted) {
    }
}
