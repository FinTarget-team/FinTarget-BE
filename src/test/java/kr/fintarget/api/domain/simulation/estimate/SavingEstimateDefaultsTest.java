package kr.fintarget.api.domain.simulation.estimate;

import kr.fintarget.api.domain.policy.recommend.LlmPolicyRecommendationSelector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 설정을 따로 주지 않았을 때 실제로 주입되는 기본값 (#257).
 * 운영 .env와 compose가 비어 있어도 이 값이 적용된다.
 */
@SpringBootTest
class SavingEstimateDefaultsTest {

    @Autowired
    private SimulationSavingEstimator estimator;

    @Autowired
    private LlmPolicyRecommendationSelector selector;

    @Test
    void 절감액_추정은_기본으로_켜지고_응답에도_노출된다() {
        assertThat(estimator.isEnabled()).isTrue();
        assertThat(estimator.isExposedInPolicyResponses()).isTrue();
    }

    @Test
    void 기준_월세_기본값은_62만원이다() {
        assertThat(estimator.baseRent()).isEqualTo(620_000L);
        assertThat(SimulationSavingEstimator.DEFAULT_BASE_RENT).isEqualTo(620_000L);
    }

    @Test
    void 리랭킹_대기_시간_기본값은_30초다() {
        // 진행 중인 미리 계산을 기다릴 때도 같은 값을 쓴다 (#243)
        assertThat(selector.timeout()).isEqualTo(Duration.ofSeconds(30));
    }
}
