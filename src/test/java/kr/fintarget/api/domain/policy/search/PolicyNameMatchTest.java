package kr.fintarget.api.domain.policy.search;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyNameMatchTest {

    private static Policy policy(String name) {
        return Policy.create(name, "설명", null, null, null, null, PolicyType.ETC, null, null, null);
    }

    @Test
    void 괄호_안_별칭과_괄호_앞_이름_연도를_뗀_이름으로_찾는다() {
        Policy stay = policy("부산 청년 임차보증금 대출 및 대출이자 지원(머물자리론)");
        Policy kpass = policy("K-패스(K패스)");
        Policy job = policy("국민취업지원제도");
        Policy rent = policy("2026 청년 월세 지원");
        List<Policy> candidates = List.of(stay, kpass, job, rent);

        assertThat(PolicyNameMatch.find(candidates, "머물자리론 지금 신청 가능해?")).containsExactly(stay);
        assertThat(PolicyNameMatch.find(candidates, "K-패스 지금 신청할 수 있어?")).containsExactly(kpass);
        assertThat(PolicyNameMatch.find(candidates, "k패스랑 뭐가 달라?")).containsExactly(kpass);
        assertThat(PolicyNameMatch.find(candidates, "국민취업지원제도 받을 수 있어?")).containsExactly(job);
        assertThat(PolicyNameMatch.find(candidates, "청년 월세 지원 나 받을 수 있어?")).containsExactly(rent);
    }

    @Test
    void 짧거나_일반적인_이름은_키로_쓰지_않는다() {
        assertThat(PolicyNameMatch.keys("청년 지원")).isEmpty();
        assertThat(PolicyNameMatch.keys("청년수당")).isEmpty();
        assertThat(PolicyNameMatch.keys("A 정책")).isEmpty();
        assertThat(PolicyNameMatch.keys("(광양시)광양학사(서울 공공기숙사) 운영")).contains("광양학사", "서울공공기숙사");
        // 괄호 안 별칭이 짧은 일반 낱말이면 쓰지 않는다 ("생활비가 부족해"에 학자금 대출이 끌려오지 않게)
        assertThat(PolicyNameMatch.keys("일반 상환 학자금 대출 (생활비)")).doesNotContain("생활비");
        assertThat(PolicyNameMatch.find(List.of(policy("일반 상환 학자금 대출 (생활비)")), "구직 중인데 생활비가 부족해")).isEmpty();
        assertThat(PolicyNameMatch.find(List.of(policy("청년 지원")), "청년 지원 정책 알려줘")).isEmpty();
    }

    @Test
    void 이름이_질문에_없으면_찾지_않고_여러_개면_긴_이름부터_최대_2건이다() {
        Policy a = policy("청년 월세 지원");
        Policy b = policy("서울 청년 월세 지원 확대");
        Policy c = policy("월세 안심계약 도움서비스");

        assertThat(PolicyNameMatch.find(List.of(a, b, c), "월세가 부담돼")).isEmpty();
        assertThat(PolicyNameMatch.find(List.of(a, b, c), "서울 청년 월세 지원 확대랑 월세 안심계약 도움서비스 알려줘"))
                .containsExactly(c, b);
        assertThat(PolicyNameMatch.find(List.of(a), null)).isEmpty();
    }
}
