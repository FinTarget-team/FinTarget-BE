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
 *
 * <p>사용자별로 세대(generation)를 함께 센다. {@link #evict}가 세대를 올리므로, 계산을 시작할 때 읽어 둔 세대로
 * {@link #putIfUnchanged}를 호출하면 그 사이에 비워진(시연 초기화된, #239) 사용자의 결과는 저장되지 않는다.
 */
@Component
public class PolicyRerankCache {

    private final Map<String, Slot> slots = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    /** 입력 해시가 같은 캐시만 돌려준다 */
    public Optional<Entry> find(String userId, String inputHash) {
        Entry entry = entry(userId);
        return entry != null && entry.inputHash().equals(inputHash) ? Optional.of(entry) : Optional.empty();
    }

    /** 지금 세대 값. 계산을 시작할 때 읽어 두고 저장할 때 {@link #putIfUnchanged}에 넘긴다 (#239) */
    public long generation(String userId) {
        Slot slot = slots.get(userId);
        return slot == null ? 0L : slot.generation();
    }

    public void put(String userId, Entry entry) {
        slots.compute(userId, (key, slot) -> new Slot(slot == null ? 0L : slot.generation(), entry));
    }

    /**
     * 읽어 둔 세대가 그대로일 때만 저장한다. 그 사이에 캐시가 비워졌으면(시연 초기화) 결과를 버리고 false (#239).
     * 한 키에 대해 {@link #evict}와 겹치지 않게 처리하므로 초기화 직후의 결과가 남지 않는다.
     */
    public boolean putIfUnchanged(String userId, long generation, Entry entry) {
        Slot updated = slots.compute(userId, (key, slot) ->
                (slot == null ? 0L : slot.generation()) == generation ? new Slot(generation, entry) : slot);
        return updated != null && updated.entry() == entry;
    }

    /** 캐시를 비우고 세대를 올린다. 진행 중인 계산의 결과도 저장되지 않는다 (#239) */
    public void evict(String userId) {
        slots.compute(userId, (key, slot) -> new Slot((slot == null ? 0L : slot.generation()) + 1, null));
    }

    /** 캐시된 사용자 수 (비워진 뒤 다시 채워지지 않은 사용자는 세지 않는다) */
    public int size() {
        return (int) slots.values().stream().filter(slot -> slot.entry() != null).count();
    }

    private Entry entry(String userId) {
        Slot slot = slots.get(userId);
        return slot == null ? null : slot.entry();
    }

    // 사용자당 캐시 1건과 세대. 세대만 남기고 캐시를 비울 수 있어야 하므로 entry는 null이 될 수 있다
    private record Slot(long generation, Entry entry) {
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
