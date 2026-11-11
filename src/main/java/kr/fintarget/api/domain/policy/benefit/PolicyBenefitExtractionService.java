package kr.fintarget.api.domain.policy.benefit;

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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 저장된 정책의 혜택을 AI로 추출해 Policy.benefit에 저장한다 (#162). 동기화 후 자동 실행과 관리자 백필이 공유한다.
 *
 * <ul>
 *   <li>원문 해시·프롬프트 버전이 저장된 값과 같은 정책은 건너뛴다 (매일 동기화마다 같은 원문을 다시 추출하지 않음)</li>
 *   <li>AI 호출은 트랜잭션 밖에서 하고, 정책 1건씩 짧은 트랜잭션으로 저장한다 (AI 응답을 기다리는 동안 DB 커넥션을 잡지 않음)</li>
 *   <li>한 건이 실패해도 나머지는 계속한다. 실패한 정책은 기존 값을 유지하고 다음 실행 때 다시 시도한다</li>
 *   <li>AI를 쓸 수 없는 상태(키 없음, 호출 한도·잔액 초과)면 나머지도 실패할 것이므로 바로 멈춘다</li>
 * </ul>
 */
@Slf4j
@Service
public class PolicyBenefitExtractionService {

    private final PolicyRepository policyRepository;
    private final PolicyBenefitExtractor extractor;
    private final TransactionTemplate transactionTemplate;
    private final int parallelism;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public PolicyBenefitExtractionService(PolicyRepository policyRepository, PolicyBenefitExtractor extractor,
                                          TransactionTemplate transactionTemplate,
                                          @Value("${policy.benefit.extraction.parallelism:4}") int parallelism) {
        this.policyRepository = policyRepository;
        this.extractor = extractor;
        this.transactionTemplate = transactionTemplate;
        this.parallelism = Math.max(1, parallelism);
    }

    /** 아직 추출하지 않았거나 원문·프롬프트 버전이 바뀐 정책만 추출한다 */
    public BenefitExtractionResult extractPending() {
        return run(false);
    }

    /** 모든 정책을 다시 추출한다 (추출 규칙을 바꾸지 않고 다시 돌려야 할 때) */
    public BenefitExtractionResult extractAll() {
        return run(true);
    }

    private BenefitExtractionResult run(boolean all) {
        if (!running.compareAndSet(false, true)) {
            throw new BenefitExtractionInProgressException();
        }
        try {
            return doRun(all);
        } finally {
            running.set(false);
        }
    }

    private BenefitExtractionResult doRun(boolean all) {
        long start = System.currentTimeMillis();
        String version = extractor.promptVersion();
        List<Target> targets = transactionTemplate.execute(status -> findTargets(all, version));

        Map<BenefitType, Integer> savedByType = new EnumMap<>(BenefitType.class);
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
            Outcome outcome = join(future);
            switch (outcome.status()) {
                case SAVED -> savedByType.merge(outcome.type(), 1, Integer::sum);
                case FAILED -> failed++;
                case SKIPPED -> skipped++;
            }
        }

        long elapsedMs = System.currentTimeMillis() - start;
        log.info("정책 혜택 추출 완료 prompt={} all={} 대상 {}건, 저장 {}, 실패 {}건, 건너뜀 {}건, 소요 {}ms",
                version, all, targets.size(), savedByType, failed, skipped, elapsedMs);
        return new BenefitExtractionResult(targets.size(), savedByType, failed, skipped, elapsedMs);
    }

    private List<Target> findTargets(boolean all, String version) {
        List<Target> targets = new ArrayList<>();
        for (Policy policy : policyRepository.findAll()) {
            BenefitSource source = BenefitSource.of(policy);
            String hash = source.contentHash();
            if (all || policy.needsBenefitExtraction(hash, version)) {
                targets.add(new Target(policy.getPolicyId(), source, hash));
            }
        }
        return targets;
    }

    private Outcome extractOne(Target target, String version, AtomicBoolean aiUnavailable) {
        if (aiUnavailable.get()) {
            return Outcome.skipped();
        }
        BenefitExtraction extraction;
        try {
            extraction = extractor.extract(target.source());
        } catch (AiClientException e) {
            // 키 없음·한도 초과·과부하·인증 실패는 다음 정책도 똑같이 실패하므로 바로 멈춘다
            if (e instanceof AiUnavailableException || e.getReason() == AiClientException.Reason.AUTH_FAILED) {
                if (aiUnavailable.compareAndSet(false, true)) {
                    log.warn("AI를 쓸 수 없어 정책 혜택 추출을 멈춥니다 reason={}", e.getReason());
                }
                return Outcome.skipped();
            }
            log.warn("정책 혜택 추출 실패 policyId={} name={} reason={}", target.policyId(), target.source().name(),
                    e.getReason());
            return Outcome.failed();
        } catch (BenefitExtractionException e) {
            log.warn("정책 혜택 추출 실패 policyId={} name={} error={}", target.policyId(), target.source().name(),
                    e.getMessage());
            return Outcome.failed();
        }

        // 추출하는 동안 동기화로 삭제됐으면 저장하지 않는다
        Boolean saved = transactionTemplate.execute(status -> policyRepository.findById(target.policyId())
                .map(policy -> {
                    policy.updateBenefit(extraction.toPolicyBenefit(version, target.sourceHash(), LocalDateTime.now()));
                    return true;
                })
                .orElse(false));
        return Boolean.TRUE.equals(saved) ? Outcome.saved(extraction.type()) : Outcome.failed();
    }

    private static Outcome join(Future<Outcome> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.failed();
        } catch (Exception e) {
            log.warn("정책 혜택 추출 작업 오류", e);
            return Outcome.failed();
        }
    }

    private record Target(UUID policyId, BenefitSource source, String sourceHash) {
    }

    private enum Status { SAVED, FAILED, SKIPPED }

    private record Outcome(Status status, BenefitType type) {
        static Outcome saved(BenefitType type) {
            return new Outcome(Status.SAVED, type);
        }

        static Outcome failed() {
            return new Outcome(Status.FAILED, null);
        }

        static Outcome skipped() {
            return new Outcome(Status.SKIPPED, null);
        }
    }
}
