package kr.fintarget.api.domain.policy.repository;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PolicyRepositoryTest {

    @Autowired
    private PolicyRepository policyRepository;

    @Test
    void 나이_소득_제한이_모두_없는_정책은_모든_사용자에게_매칭된다() {
        policyRepository.save(Policy.create("전체 대상 정책", "설명", null, null, null, 100000L, PolicyType.ETC, "전국", null, null));

        List<Policy> result = policyRepository.findMatchingPolicies(25, 25, 1_000_000L, null);

        assertThat(result).extracting(Policy::getName).contains("전체 대상 정책");
    }

    @Test
    void 나이_상한이_없는_정책은_최소_나이_이상이면_매칭된다() {
        policyRepository.save(Policy.create("청년 이상 정책", "설명", 19, null, 5_000_000L, 100000L, PolicyType.ETC, "전국", null, null));

        assertThat(policyRepository.findMatchingPolicies(19, 19, 1_000_000L, null))
                .extracting(Policy::getName).contains("청년 이상 정책");
        assertThat(policyRepository.findMatchingPolicies(18, 18, 1_000_000L, null))
                .extracting(Policy::getName).doesNotContain("청년 이상 정책");
    }

    @Test
    void 나이_하한이_없는_정책은_최대_나이_이하면_매칭된다() {
        policyRepository.save(Policy.create("상한만 있는 정책", "설명", null, 34, null, 100000L, PolicyType.ETC, "전국", null, null));

        assertThat(policyRepository.findMatchingPolicies(34, 34, 1_000_000L, null))
                .extracting(Policy::getName).contains("상한만 있는 정책");
        assertThat(policyRepository.findMatchingPolicies(35, 35, 1_000_000L, null))
                .extracting(Policy::getName).doesNotContain("상한만 있는 정책");
    }

    @Test
    void 소득_제한이_있는_정책은_초과시_매칭되지_않는다() {
        policyRepository.save(Policy.create("저소득 정책", "설명", 18, 65, 3_000_000L, 100000L, PolicyType.ETC, "전국", null, null));

        assertThat(policyRepository.findMatchingPolicies(25, 25, 3_000_000L, null))
                .extracting(Policy::getName).contains("저소득 정책");
        assertThat(policyRepository.findMatchingPolicies(25, 25, 3_000_001L, null))
                .extracting(Policy::getName).doesNotContain("저소득 정책");
    }

    @Test
    void policyType이_지정되면_해당_타입만_매칭된다() {
        policyRepository.save(Policy.create("주거정책", "설명", null, null, null, 100000L, PolicyType.HOUSING, "전국", null, null));
        policyRepository.save(Policy.create("취업정책", "설명", null, null, null, 100000L, PolicyType.ETC, "전국", null, null));

        List<Policy> result = policyRepository.findMatchingPolicies(25, 25, 1_000_000L, PolicyType.HOUSING);

        assertThat(result).extracting(Policy::getName).containsExactly("주거정책");
    }

    @Test
    void 사용자_나이_구간이_정책_나이_범위와_겹치면_매칭된다() {
        policyRepository.save(Policy.create("19~39 정책", "설명", 19, 39, null, null, PolicyType.ETC, null, null, null));

        // 올해 19세가 되는 사용자(생일 전 18, 생일 후 19)와 올해 40세가 되는 사용자(생일 전 39, 생일 후 40)는 포함
        assertThat(policyRepository.findMatchingPolicies(18, 19, 1_000_000L, null))
                .extracting(Policy::getName).contains("19~39 정책");
        assertThat(policyRepository.findMatchingPolicies(39, 40, 1_000_000L, null))
                .extracting(Policy::getName).contains("19~39 정책");
        assertThat(policyRepository.findMatchingPolicies(25, 26, 1_000_000L, null))
                .extracting(Policy::getName).contains("19~39 정책");
    }

    @Test
    void 사용자_나이_구간이_정책_나이_범위와_겹치지_않으면_매칭되지_않는다() {
        policyRepository.save(Policy.create("19~39 정책", "설명", 19, 39, null, null, PolicyType.ETC, null, null, null));

        assertThat(policyRepository.findMatchingPolicies(17, 18, 1_000_000L, null))
                .extracting(Policy::getName).doesNotContain("19~39 정책");
        assertThat(policyRepository.findMatchingPolicies(40, 41, 1_000_000L, null))
                .extracting(Policy::getName).doesNotContain("19~39 정책");
    }

    @Test
    void 나이_범위의_한쪽만_있는_정책도_구간_겹침으로_판정한다() {
        policyRepository.save(Policy.create("19세 이상", "설명", 19, null, null, null, PolicyType.ETC, null, null, null));
        policyRepository.save(Policy.create("34세 이하", "설명", null, 34, null, null, PolicyType.ETC, null, null, null));

        assertThat(policyRepository.findMatchingPolicies(18, 19, 1_000_000L, null))
                .extracting(Policy::getName).contains("19세 이상", "34세 이하");
        assertThat(policyRepository.findMatchingPolicies(17, 18, 1_000_000L, null))
                .extracting(Policy::getName).containsExactly("34세 이하");
        assertThat(policyRepository.findMatchingPolicies(34, 35, 1_000_000L, null))
                .extracting(Policy::getName).contains("19세 이상", "34세 이하");
        assertThat(policyRepository.findMatchingPolicies(35, 36, 1_000_000L, null))
                .extracting(Policy::getName).containsExactly("19세 이상");
    }
}
