package kr.fintarget.api.domain.policy.condition;

import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiUnavailableException;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.repository.PolicyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 저장된 정책의 신청 조건을 AI로 추출해 Policy.condition에 저장한다 (#220). 동기화 후 자동 실행과 관리자 백필이 공유한다.
 * 구조는 혜택 추출(PolicyBenefitExtractionService, #162)과 같다.
 *
 * <ul>
 *   <li>원문 해시·프롬프트 버전이 저장된 값과 같은 정책은 건너뛴다 (매일 동기화마다 같은 원문을 다시 추출하지 않음)</li>
 *   <li>AI 호출은 트랜잭션 밖에서 하고, 정책 1건씩 짧은 트랜잭션으로 저장한다</li>
 *   <li>응답을 해석하지 못하면 모든 조건을 UNKNOWN으로 저장한다 (UNKNOWN은 추천에서 제외하지 않으므로 안전한 쪽이다.
 *       같은 원문으로 다시 물어도 결과가 같을 가능성이 높아, 해시를 남겨 매일 다시 호출하지 않는다)</li>
 *   <li>AI 호출이 실패하면(재시도 1회 후) 저장하지 않는다. 기존 값(없으면 null=UNKNOWN)을 유지하고 다음 실행 때 다시 시도한다</li>
 *   <li>AI를 쓸 수 없는 상태(키 없음, 호출 한도·잔액 초과, 인증 실패)면 나머지도 실패할 것이므로 바로 멈춘다</li>
 *   <li>호출 사이에 짧게 쉰다 (policy.condition.extraction.delay-ms). 900건 전체 추출이 OpenAI 호출 한도에 걸리지 않게 한다</li>
 * </ul>
 */
@Slf4j
@Service
public class PolicyConditionExtractionService {

    private final PolicyRepository policyRepository;
    private final PolicyConditionExtractor extractor;
    private final TransactionTemplate transactionTemplate;
    private final int parallelism;
    private final long delayMs;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public PolicyConditionExtractionService(PolicyRepository policyRepository, PolicyConditionExtractor extractor,
                                            TransactionTemplate transactionTemplate,
                                            @Value("${policy.condition.extraction.parallelism:2}") int parallelism,
                                            @Value("${policy.condition.extraction.delay-ms:200}") long delayMs) {
        this.policyRepository = policyRepository;
        this.extractor = extractor;
        this.transactionTemplate = transactionTemplate;
        this.parallelism = Math.max(1, parallelism);
        this.delayMs = Math.max(0, delayMs);
    }

    /** 아직 추출하지 않았거나 원문·프롬프트 버전이 바뀐 정책만 추출한다 */
    public ConditionExtractionResult extractPending() {
        return run(false);
    }

    /** 모든 정책을 다시 추출한다 */
    public ConditionExtractionResult extractAll() {
        return run(true);
    }

    private ConditionExtractionResult run(boolean all) {
        if (!running.compareAndSet(false, true)) {
            throw new ConditionExtractionInProgressException();
        }
        try {
            return doRun(all);
        } finally {
            running.set(false);
        }
    }

    private ConditionExtractionResult doRun(boolean all) {
        long start = System.currentTimeMillis();
        String version = extractor.promptVersion();
        List<Target> targets = transactionTemplate.execute(status -> findTargets(all, version));

        int saved = 0;
        int unparsed = 0;
        int failed = 0;
        int skipped = 0;
        AtomicBoolean aiUnavailable = new AtomicBoolean(false);

        List<Future<Outcome>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(parallelism)) {
            for (Target target : targets) {
                futures.add(executor.submit(() -> extractOne(target, version, aiUnavailable)));
            }
        }
        for (Future<Outcome> future : futures) {
            switch (join(future)) {
                case SAVED -> saved++;
                case UNPARSED -> {
                    saved++;
                    unparsed++;
                }
                case FAILED -> failed++;
                case SKIPPED -> skipped++;
            }
        }

        long elapsedMs = System.currentTimeMillis() - start;
        log.info("정책 조건 추출 완료 prompt={} all={} 대상 {}건, 저장 {}건(해석 실패 {}건), 실패 {}건, 건너뜀 {}건, 소요 {}ms",
                version, all, targets.size(), saved, unparsed, failed, skipped, elapsedMs);
        return new ConditionExtractionResult(targets.size(), saved, unparsed, failed, skipped, elapsedMs);
    }

    private List<Target> findTargets(boolean all, String version) {
        List<Target> targets = new ArrayList<>();
        for (Policy policy : policyRepository.findAll()) {
            ConditionSource source = ConditionSource.of(policy);
            String hash = source.contentHash();
            if (all || policy.needsConditionExtraction(hash, version)) {
                targets.add(new Target(policy.getPolicyId(), source, hash));
            }
        }
        return targets;
    }

    private Outcome extractOne(Target target, String version, AtomicBoolean aiUnavailable) {
        if (aiUnavailable.get()) {
            return Outcome.SKIPPED;
        }
        ConditionExtraction extraction;
        Outcome outcome = Outcome.SAVED;
        try {
            extraction = extractor.extract(target.source());
        } catch (AiClientException e) {
            if (e instanceof AiUnavailableException || e.getReason() == AiClientException.Reason.AUTH_FAILED) {
                if (aiUnavailable.compareAndSet(false, true)) {
                    log.warn("AI를 쓸 수 없어 정책 조건 추출을 멈춥니다 reason={}", e.getReason());
                }
                return Outcome.SKIPPED;
            }
            log.warn("정책 조건 추출 실패 policyId={} name={} reason={}", target.policyId(), target.source().name(),
                    e.getReason());
            return Outcome.FAILED;
        } catch (ConditionExtractionException e) {
            log.warn("정책 조건 추출 응답 해석 실패, UNKNOWN으로 저장 policyId={} name={} error={}",
                    target.policyId(), target.source().name(), e.getMessage());
            extraction = ConditionExtraction.unknown();
            outcome = Outcome.UNPARSED;
        } finally {
            pause();
        }

        ConditionExtraction toSave = extraction;
        // 추출하는 동안 동기화로 삭제됐으면 저장하지 않는다
        Boolean stored = transactionTemplate.execute(status -> policyRepository.findById(target.policyId())
                .map(policy -> {
                    policy.updateCondition(toSave.toPolicyCondition(version, target.sourceHash(), LocalDateTime.now()));
                    return true;
                })
                .orElse(false));
        return Boolean.TRUE.equals(stored) ? outcome : Outcome.FAILED;
    }

    private void pause() {
        if (delayMs == 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Outcome join(Future<Outcome> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.FAILED;
        } catch (Exception e) {
            log.warn("정책 조건 추출 작업 오류", e);
            return Outcome.FAILED;
        }
    }

    private record Target(UUID policyId, ConditionSource source, String sourceHash) {
    }

    private enum Outcome { SAVED, UNPARSED, FAILED, SKIPPED }
}
