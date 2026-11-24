package kr.fintarget.api.domain.policy.recommend.rerank;

import jakarta.annotation.PreDestroy;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.recommend.LlmPolicyRecommendationSelector;
import kr.fintarget.api.domain.policy.recommend.LlmPolicyRecommendationSelector.RefreshOutcome;
import kr.fintarget.api.domain.policy.service.PolicyService;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.event.UserProfileChangedEvent;
import kr.fintarget.api.domain.user.repository.UserRepository;
import kr.fintarget.api.domain.user.validation.BirthYearValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 추천 리랭킹 미리 계산 (#229). 목록 조회 때 LLM을 기다리지 않도록 캐시를 미리 채운다.
 *
 * <ul>
 *   <li>앱 시작 완료 후 온보딩을 마친 전체 사용자 (배포로 메모리 캐시가 비기 때문)</li>
 *   <li>온보딩 완료·프로필 수정 커밋 후 해당 사용자 ({@link UserProfileChangedEvent})</li>
 *   <li>동기화 후 조건 추출이 끝나면 전체 사용자 (PolicySyncOrchestrator)</li>
 *   <li>관리자 API로 전체 사용자 수동 실행</li>
 * </ul>
 * 작업은 전용 스레드 1개에서 순서대로 돌고, 호출 사이에 policy.recommend.rerank.precompute-delay-ms(기본 1000ms)만큼 쉰다.
 * 입력이 같은 캐시가 있는 사용자는 LLM을 호출하지 않는다. 전체 계산이 진행 중에 다시 요청되면 끝난 뒤 한 번 더 돈다.
 * 리랭킹을 쓸 수 없으면(꺼짐, AI 키 없음) 아무것도 하지 않는다.
 */
@Slf4j
@Service
public class PolicyRerankPrecomputer {

    private final LlmPolicyRecommendationSelector selector;
    private final PolicyService policyService;
    private final UserRepository userRepository;
    private final TransactionTemplate readOnlyTransaction;
    private final long delayMs;
    private final ExecutorService executor;
    private final AtomicBoolean allRunning = new AtomicBoolean(false);
    private final AtomicBoolean allPending = new AtomicBoolean(false);

    @Autowired
    public PolicyRerankPrecomputer(LlmPolicyRecommendationSelector selector, PolicyService policyService,
                                   UserRepository userRepository, PlatformTransactionManager transactionManager,
                                   @Value("${policy.recommend.rerank.precompute-delay-ms:1000}") long delayMs) {
        this(selector, policyService, userRepository, transactionManager, delayMs,
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "policy-rerank-precompute");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    PolicyRerankPrecomputer(LlmPolicyRecommendationSelector selector, PolicyService policyService,
                            UserRepository userRepository, PlatformTransactionManager transactionManager,
                            long delayMs, ExecutorService executor) {
        this.selector = selector;
        this.policyService = policyService;
        this.userRepository = userRepository;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.delayMs = Math.max(0, delayMs);
        this.executor = executor;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (requestAll()) {
            log.info("앱 시작 후 추천 리랭킹 전체 미리 계산을 예약했습니다");
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserProfileChanged(UserProfileChangedEvent event) {
        requestUser(event.userId());
    }

    /** 해당 사용자 1명을 비동기로 미리 계산한다. 리랭킹을 쓸 수 없으면 false */
    public boolean requestUser(String userId) {
        if (!selector.isAvailable() || userId == null) {
            return false;
        }
        executor.execute(() -> refreshUser(userId));
        return true;
    }

    /**
     * 온보딩을 마친 전체 사용자를 비동기로 미리 계산한다. 리랭킹을 쓸 수 없으면 false.
     * 이미 진행 중이면 끝난 뒤 한 번 더 돌도록 표시하고 true.
     */
    public boolean requestAll() {
        if (!selector.isAvailable()) {
            return false;
        }
        if (!allRunning.compareAndSet(false, true)) {
            allPending.set(true);
            return true;
        }
        executor.execute(this::runAll);
        return true;
    }

    /** 전체 미리 계산이 진행 중인지 */
    public boolean isRunningAll() {
        return allRunning.get();
    }

    private void runAll() {
        try {
            do {
                allPending.set(false);
                refreshAll();
            } while (allPending.get() && selector.isAvailable());
        } finally {
            allRunning.set(false);
            // 반복 조건을 확인한 직후 들어온 요청을 놓치지 않는다
            if (allPending.getAndSet(false)) {
                requestAll();
            }
        }
    }

    private void refreshAll() {
        long start = System.currentTimeMillis();
        List<String> userIds;
        try {
            userIds = readOnlyTransaction.execute(status -> userRepository.findIdsByOnboardingCompletedTrue());
        } catch (RuntimeException e) {
            log.error("추천 리랭킹 전체 미리 계산 - 사용자 조회 실패", e);
            return;
        }
        Map<RefreshOutcome, Integer> counts = new EnumMap<>(RefreshOutcome.class);
        for (String userId : userIds == null ? List.<String>of() : userIds) {
            if (Thread.currentThread().isInterrupted() || !selector.isAvailable()) {
                break;
            }
            RefreshOutcome outcome = refreshUser(userId);
            counts.merge(outcome, 1, Integer::sum);
            if (outcome == RefreshOutcome.COMPUTED || outcome == RefreshOutcome.FAILED) {
                if (!pause()) {
                    break;
                }
            }
        }
        log.info("추천 리랭킹 전체 미리 계산 완료 대상 {}명, 결과 {}, 소요 {}ms",
                userIds == null ? 0 : userIds.size(), counts, System.currentTimeMillis() - start);
    }

    RefreshOutcome refreshUser(String userId) {
        try {
            LocalDate today = LocalDate.now(BirthYearValidator.ZONE);
            Input input = readOnlyTransaction.execute(status -> userRepository.findById(userId)
                    .map(user -> new Input(user, policyService.sortedCandidatePolicies(user, null, today)))
                    .orElse(null));
            if (input == null) {
                return RefreshOutcome.NOT_NEEDED;
            }
            // 정책·사용자 값은 모두 즉시 로딩(임베디드)이라 트랜잭션 밖에서 LLM을 호출해도 된다
            return selector.refresh(input.user(), input.sorted(), today);
        } catch (RuntimeException e) {
            log.error("추천 리랭킹 미리 계산 실패 userId={}", userId, e);
            return RefreshOutcome.FAILED;
        }
    }

    private boolean pause() {
        if (delayMs == 0) {
            return true;
        }
        try {
            Thread.sleep(delayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private record Input(User user, List<Policy> sorted) {
    }
}
