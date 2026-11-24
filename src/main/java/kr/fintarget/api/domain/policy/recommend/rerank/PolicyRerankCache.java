package kr.fintarget.api.domain.policy.recommend.rerank;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사용자별 리랭킹 결과 메모리 캐시 (#229). DB에 저장하지 않으므로 배포(재시작) 때 비고, 앱 시작 후 미리 계산으로 다시 채운다.
 * 사용자당 1건(전체 유형 추천 목록)만 둔다. 입력 해시가 다르면 쓰지 않는다.
 * 같은 사용자의 계산이 동시에 두 번 돌지 않도록 진행 중 표시도 함께 관리한다.
 */
@Component
public class PolicyRerankCache {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    /** 입력 해시가 같은 캐시만 돌려준다 */
    public Optional<Entry> find(String userId, String inputHash) {
        Entry entry = entries.get(userId);
        return entry != null && entry.inputHash().equals(inputHash) ? Optional.of(entry) : Optional.empty();
    }

    public void put(String userId, Entry entry) {
        entries.put(userId, entry);
    }

    public void evict(String userId) {
        entries.remove(userId);
    }

    public int size() {
        return entries.size();
    }

    /** 계산을 시작한다. 같은 사용자의 계산이 이미 진행 중이면 false */
    public boolean tryStart(String userId) {
        return inFlight.add(userId);
    }

    public void finish(String userId) {
        inFlight.remove(userId);
    }

    /**
     * @param policyIds     LLM이 고른 정책 ID (적합한 순서, 후보 안의 정책만)
     * @param reasons       policyIds와 같은 순서의 선택 이유 (응답에 넣지 않음)
     * @param inputHash     사용자 입력값 + 후보 정책 ID 목록 + 프롬프트 버전의 해시
     * @param promptVersion 프롬프트 버전
     * @param model         응답에 기록된 모델명
     * @param createdAt     생성 시각
     */
    public record Entry(List<UUID> policyIds, List<String> reasons, String inputHash, String promptVersion,
                        String model, LocalDateTime createdAt) {

        public Entry {
            policyIds = List.copyOf(policyIds);
            reasons = List.copyOf(reasons);
        }
    }
}
