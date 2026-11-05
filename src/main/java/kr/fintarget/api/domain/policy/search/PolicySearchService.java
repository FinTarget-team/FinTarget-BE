package kr.fintarget.api.domain.policy.search;

import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiEmbeddingClient;
import kr.fintarget.api.domain.ai.usage.AiUsageFeature;
import kr.fintarget.api.domain.ai.usage.AiUsageLimiter;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.search.PolicyChunkSearchRepository.ChunkMatch;
import kr.fintarget.api.domain.policy.service.PolicyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 자연어 질문으로 사용자 조건에 맞는 정책을 찾는다 (#182). 2단계로 좁힌다.
 * <ol>
 *   <li>맞춤 정책과 같은 조건(나이·소득·유형·지역)으로 후보 정책 ID를 만든다 ({@link PolicyService#findCandidatePolicies})</li>
 *   <li>그 후보 안에서만 질문 벡터에 가까운 청크를 조회하고, 정책별 최고 점수로 묶는다</li>
 * </ol>
 *
 * <p>정책별 묶기·유사도 하한·k 적용을 Java에서 하는 이유는 {@link PolicyChunkSearchRepository} 참고.
 * k와 하한값은 설정값이며 최종값은 검색 평가(#184)로 정한다.
 */
@Slf4j
@Service
public class PolicySearchService {

    static final int DEFAULT_K = 5;
    // 관련 있는 결과는 거리 0.15 안쪽에 모여 있었다(2026-11-04 운영 표본: 같은 주제 다른 정책 0.1398).
    // 처음부터 조이면 #184가 재현율을 측정할 수 없어, 무관한 결과만 자르는 느슨한 값으로 둔다
    static final double DEFAULT_MAX_DISTANCE = 0.5;
    // 한 정책이 청크 여러 개를 차지하므로 k개 정책을 채우려면 청크를 더 많이 조회해야 한다
    // (2026-11-04 운영: 청크 976개 / 정책 883건 ≈ 1.1)
    static final int DEFAULT_CHUNK_MULTIPLIER = 3;
    // 검색 API의 사용자별 하루 호출 수 (#183). 임베딩만 호출해 비용이 작아 통합 AI 답변(20)보다 넉넉하게 둔다
    static final int DEFAULT_DAILY_LIMIT = 50;

    private final PolicyService policyService;
    private final PolicyChunkSearchRepository searchRepository;
    private final AiEmbeddingClient embeddingClient;
    private final AiUsageLimiter usageLimiter;
    private final int k;
    private final double maxDistance;
    private final int chunkMultiplier;
    private final int dailyLimit;

    public PolicySearchService(PolicyService policyService,
                               PolicyChunkSearchRepository searchRepository,
                               AiEmbeddingClient embeddingClient,
                               AiUsageLimiter usageLimiter,
                               @Value("${policy.search.k:" + DEFAULT_K + "}") int k,
                               @Value("${policy.search.max-distance:" + DEFAULT_MAX_DISTANCE + "}") double maxDistance,
                               @Value("${policy.search.chunk-multiplier:" + DEFAULT_CHUNK_MULTIPLIER + "}") int chunkMultiplier,
                               @Value("${policy.search.daily-limit:" + DEFAULT_DAILY_LIMIT + "}") int dailyLimit) {
        if (k < 1) {
            throw new IllegalArgumentException("policy.search.k는 1 이상이어야 합니다: " + k);
        }
        if (chunkMultiplier < 1) {
            throw new IllegalArgumentException("policy.search.chunk-multiplier는 1 이상이어야 합니다: " + chunkMultiplier);
        }
        if (dailyLimit < 1) {
            throw new IllegalArgumentException("policy.search.daily-limit는 1 이상이어야 합니다: " + dailyLimit);
        }
        this.policyService = policyService;
        this.searchRepository = searchRepository;
        this.embeddingClient = embeddingClient;
        this.usageLimiter = usageLimiter;
        this.k = k;
        this.maxDistance = maxDistance;
        this.chunkMultiplier = chunkMultiplier;
        this.dailyLimit = dailyLimit;
    }

    /**
     * 질문과 가까운 정책을 최대 k건, 유사도가 가까운 순서로 돌려준다.
     * 나이·소득을 모르는 사용자와 하한값을 넘는 결과만 있는 경우는 빈 목록이다 (오류가 아니다).
     *
     * 검색 API 경로이며 사용자별 일일 한도(policy.search.daily-limit)를 1회 차감한다.
     * 후보가 없으면 임베딩을 호출하지 않으므로 차감하지 않고, 임베딩 호출이 실패하면 되돌린다.
     *
     * @throws kr.fintarget.api.domain.ai.usage.AiUsageLimitExceededException 오늘 검색 한도를 모두 썼을 때 (429)
     * @throws kr.fintarget.api.domain.ai.client.AiUnavailableException 임베딩 키가 없을 때 (503)
     * @throws kr.fintarget.api.domain.ai.client.AiCallFailedException  임베딩 호출이 실패했을 때 (502)
     */
    @Transactional(readOnly = true)
    public List<PolicySearchHit> search(String userId, String question) {
        List<Policy> candidates = policyService.findCandidatePolicies(userId, null);
        if (candidates.isEmpty()) {
            return List.of();
        }

        AiUsageLimiter.Ticket ticket = usageLimiter.acquire(userId, AiUsageFeature.POLICY_SEARCH, dailyLimit);
        try {
            return searchCandidates(candidates, question);
        } catch (AiClientException e) {
            usageLimiter.release(ticket);
            throw e;
        }
    }

    /**
     * 이미 고른 후보 정책 안에서 검색한다. 한도를 차감하지 않으며, 통합 AI 답변(#183)이 자기 한도를 차감한 뒤 호출한다.
     *
     * @param candidates 사용자 조건에 맞는 정책 (비어 있으면 임베딩 없이 빈 목록)
     */
    @Transactional(readOnly = true)
    public List<PolicySearchHit> searchCandidates(List<Policy> candidates, String question) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        // 질문 임베딩은 후보가 있을 때만 호출한다 (빈 결과가 뻔한 요청에 비용을 쓰지 않도록)
        float[] questionVector = embeddingClient.embed(List.of(question)).getFirst();

        Map<UUID, Policy> byId = new LinkedHashMap<>();
        List<UUID> policyIds = new ArrayList<>(candidates.size());
        for (Policy policy : candidates) {
            byId.put(policy.getPolicyId(), policy);
            policyIds.add(policy.getPolicyId());
        }

        int chunkLimit = k * chunkMultiplier;
        List<ChunkMatch> matches = searchRepository.search(policyIds, questionVector, chunkLimit);

        List<PolicySearchHit> hits = groupByPolicy(matches, byId);
        if (hits.size() < k && matches.size() == chunkLimit) {
            // 조회 상한에 걸려 k개를 못 채웠다. 같은 정책의 청크가 상위를 차지했다는 뜻이므로
            // policy.search.chunk-multiplier를 올려야 한다
            log.warn("정책 검색 결과가 k보다 적음: k={} 묶은 결과={}건 조회 청크={}건(상한) chunkMultiplier={}",
                    k, hits.size(), matches.size(), chunkMultiplier);
        }
        return hits;
    }

    /**
     * 같은 정책의 청크가 여러 개 나오면 가장 가까운 것 하나로 묶는다.
     * matches가 거리순이므로 정책을 처음 만났을 때가 그 정책의 최고 점수다.
     */
    private List<PolicySearchHit> groupByPolicy(List<ChunkMatch> matches, Map<UUID, Policy> byId) {
        Map<UUID, PolicySearchHit> best = new LinkedHashMap<>();
        for (ChunkMatch match : matches) {
            if (match.distance() > maxDistance || best.containsKey(match.policyId())) {
                continue;
            }
            Policy policy = byId.get(match.policyId());
            if (policy == null) {
                // 후보로 넘긴 ID만 조회하므로 정상적으로는 발생하지 않는다
                continue;
            }
            best.put(match.policyId(), new PolicySearchHit(
                    policy, match.chunkId(), match.sectionLabel(), match.content(), match.distance()));
            if (best.size() == k) {
                break;
            }
        }
        return List.copyOf(best.values());
    }
}
