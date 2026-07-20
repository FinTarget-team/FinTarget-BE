package kr.fintarget.api.domain.policy.repository;

import kr.fintarget.api.domain.policy.entity.Policy;
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
        policyRepository.save(Policy.create("전체 대상 정책", "설명", null, null, null, 100000L, "GENERAL", "전국"));

        List<Policy> result = policyRepository.findMatchingPolicies(25, 1_000_000L, null);

        assertThat(result).extracting(Policy::getName).contains("전체 대상 정책");
    }

    @Test
    void 나이_상한이_없는_정책은_최소_나이_이상이면_매칭된다() {
        policyRepository.save(Policy.create("청년 이상 정책", "설명", 19, null, 5_000_000L, 100000L, "GENERAL", "전국"));

        assertThat(policyRepository.findMatchingPolicies(19, 1_000_000L, null))
                .extracting(Policy::getName).contains("청년 이상 정책");
        assertThat(policyRepository.findMatchingPolicies(18, 1_000_000L, null))
                .extracting(Policy::getName).doesNotContain("청년 이상 정책");
    }

    @Test
    void 소득_제한이_있는_정책은_초과시_매칭되지_않는다() {
        policyRepository.save(Policy.create("저소득 정책", "설명", 18, 65, 3_000_000L, 100000L, "GENERAL", "전국"));

        assertThat(policyRepository.findMatchingPolicies(25, 3_000_000L, null))
                .extracting(Policy::getName).contains("저소득 정책");
        assertThat(policyRepository.findMatchingPolicies(25, 3_000_001L, null))
                .extracting(Policy::getName).doesNotContain("저소득 정책");
    }

    @Test
    void policyType이_지정되면_해당_타입만_매칭된다() {
        policyRepository.save(Policy.create("주거정책", "설명", null, null, null, 100000L, "HOUSING", "전국"));
        policyRepository.save(Policy.create("취업정책", "설명", null, null, null, 100000L, "EMPLOYMENT", "전국"));

        List<Policy> result = policyRepository.findMatchingPolicies(25, 1_000_000L, "HOUSING");

        assertThat(result).extracting(Policy::getName).containsExactly("주거정책");
    }
}
