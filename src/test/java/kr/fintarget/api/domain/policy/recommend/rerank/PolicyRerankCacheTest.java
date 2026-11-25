package kr.fintarget.api.domain.policy.recommend.rerank;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 캐시 저장·조회와, 시연 초기화(evict)가 진행 중인 계산 결과를 버리게 하는 세대 확인 (#239).
 */
class PolicyRerankCacheTest {

    private static final String USER = "user-1";
    private static final String HASH = "hash-1";

    private final PolicyRerankCache cache = new PolicyRerankCache();

    private static PolicyRerankCache.Entry entry(String hash) {
        return new PolicyRerankCache.Entry(List.of(UUID.randomUUID()), List.of("이유"), hash, "policy-rerank-v1",
                "gpt-6.1-sol", LocalDateTime.now());
    }

    @Test
    void 입력_해시가_같을_때만_캐시를_돌려준다() {
        cache.put(USER, entry(HASH));

        assertThat(cache.find(USER, HASH)).isPresent();
        assertThat(cache.find(USER, "다른-해시")).isEmpty();
        assertThat(cache.find("다른-사용자", HASH)).isEmpty();
    }

    @Test
    void 비우면_캐시가_사라지고_캐시된_사용자_수에서도_빠진다() {
        cache.put(USER, entry(HASH));
        assertThat(cache.size()).isEqualTo(1);

        cache.evict(USER);

        assertThat(cache.find(USER, HASH)).isEmpty();
        assertThat(cache.size()).isZero();
    }

    @Test
    void 계산_중에_비워졌으면_결과를_저장하지_않는다() {
        long generation = cache.generation(USER);

        cache.evict(USER);

        assertThat(cache.putIfUnchanged(USER, generation, entry(HASH))).isFalse();
        assertThat(cache.find(USER, HASH)).isEmpty();
        assertThat(cache.size()).isZero();
    }

    @Test
    void 계산_중에_비워지지_않았으면_결과를_저장한다() {
        long generation = cache.generation(USER);

        assertThat(cache.putIfUnchanged(USER, generation, entry(HASH))).isTrue();
        assertThat(cache.find(USER, HASH)).isPresent();
    }

    @Test
    void 비운_뒤_새로_시작한_계산은_저장된다() {
        cache.put(USER, entry(HASH));
        cache.evict(USER);

        long generation = cache.generation(USER);
        assertThat(cache.putIfUnchanged(USER, generation, entry("hash-2"))).isTrue();
        assertThat(cache.find(USER, "hash-2")).isPresent();
    }

    @Test
    void 두_번_비우면_첫_계산도_두번째_계산도_저장되지_않는다() {
        long first = cache.generation(USER);
        cache.evict(USER);
        long second = cache.generation(USER);
        cache.evict(USER);

        assertThat(cache.putIfUnchanged(USER, first, entry(HASH))).isFalse();
        assertThat(cache.putIfUnchanged(USER, second, entry(HASH))).isFalse();
        assertThat(cache.size()).isZero();
    }

    @Test
    void 진행_중_표시는_사용자별로_한_번만_얻는다() {
        assertThat(cache.tryStart(USER, HASH)).isTrue();
        assertThat(cache.tryStart(USER, HASH)).isFalse();
        assertThat(cache.tryStart(USER, "다른 입력")).isFalse();

        cache.finish(USER);

        assertThat(cache.inFlight(USER)).isEmpty();
        assertThat(cache.tryStart(USER, HASH)).isTrue();
    }

    @Test
    void 진행_중인_계산은_입력_해시와_지난_시간을_알려주고_끝나면_기다리던_쪽을_깨운다() throws Exception {
        cache.tryStart(USER, HASH);
        PolicyRerankCache.InFlight inFlight = cache.inFlight(USER).orElseThrow();
        assertThat(inFlight.inputHash()).isEqualTo(HASH);
        assertThat(inFlight.elapsed().isNegative()).isFalse();

        // 끝나지 않으면 정한 시간만 기다리고 false
        long start = System.nanoTime();
        assertThat(inFlight.await(Duration.ofMillis(100))).isFalse();
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1_000);
        assertThat(inFlight.await(Duration.ZERO)).isFalse();

        CompletableFuture<Boolean> waiter = CompletableFuture.supplyAsync(() -> {
            try {
                return inFlight.await(Duration.ofSeconds(5));
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });
        cache.finish(USER);

        assertThat(waiter.get(2, TimeUnit.SECONDS)).isTrue();
        assertThat(inFlight.await(Duration.ZERO)).isTrue();
    }
}
