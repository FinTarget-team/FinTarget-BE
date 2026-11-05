package kr.fintarget.api.domain.policy.search.eval;

import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.CaseType;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.Profile;
import kr.fintarget.api.domain.policy.search.eval.PolicySearchEvalSet.SearchCase;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.validation.BirthYearValidator;
import org.junit.jupiter.api.Test;

import java.time.Year;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 검색 평가셋 파일 형식과 프로필 변환 (#184). 문항은 사람이 채우므로 구조만 확인한다.
 */
class PolicySearchEvalSetTest {

    @Test
    void 평가셋_파일을_읽고_프로필_7종과_유형별_예시가_있다() {
        PolicySearchEvalSet evalSet = PolicySearchEvalSet.load();

        assertThat(evalSet.profiles()).containsOnlyKeys(
                "seoul-25", "busan-29", "gyeonggi-34", "chungnam-22", "jeonnam-39", "ulsan-high", "incomplete");
        assertThat(evalSet.cases()).extracting(SearchCase::type).contains(CaseType.values());
        assertThat(evalSet.guide()).containsKeys("id", "type", "profile", "question", "relevant", "loanIntent", "note");
    }

    @Test
    void 프로필은_만_나이로_적고_나이_구간은_그_나이와_한_살_아래다() {
        Profile profile = new Profile("JEONNAM", 39, 3_000_000L, null);

        Year thisYear = Year.now(BirthYearValidator.ZONE);
        User user = profile.toUser("eval-1", thisYear);

        assertThat(user.getBirthYear()).isEqualTo(thisYear.getValue() - 39);
        assertThat(user.ageRange().lower()).isEqualTo(38);
        assertThat(user.ageRange().upper()).isEqualTo(39);
        assertThat(user.getRegion()).isEqualTo("JEONNAM");
    }

    @Test
    void 조건_정보_없는_프로필은_나이_구간이_없다() {
        User user = PolicySearchEvalSet.load().profiles().get("incomplete").toUser("eval-2", Year.now(BirthYearValidator.ZONE));

        assertThat(user.ageRange()).isNull();
        assertThat(user.getIncome()).isNull();
    }

    @Test
    void 대출_의도는_생략하면_LOAN_유형만_참이다() {
        assertThat(new SearchCase("a", CaseType.LOAN, "p", "q", null, null, null).isLoanIntent()).isTrue();
        assertThat(new SearchCase("b", CaseType.BENEFIT_SITUATION, "p", "q", null, null, null).isLoanIntent()).isFalse();
        assertThat(new SearchCase("c", CaseType.BENEFIT_SITUATION, "p", "q", null, true, null).isLoanIntent()).isTrue();
    }

    @Test
    void 문항_ID_중복_없는_프로필_무관_문항의_정답을_거부한다() {
        Map<String, Profile> profiles = Map.of("p", new Profile("SEOUL", 25, 1L, null));
        SearchCase ok = new SearchCase("a", CaseType.POLICY_NAME, "p", "q", null, null, null);

        assertThatThrownBy(() -> new PolicySearchEvalSet("d", Map.of(), profiles, List.of(ok, ok)))
                .hasMessageContaining("중복");
        assertThatThrownBy(() -> new PolicySearchEvalSet("d", Map.of(), profiles,
                List.of(new SearchCase("b", CaseType.POLICY_NAME, "없음", "q", null, null, null))))
                .hasMessageContaining("없는 프로필");
        assertThatThrownBy(() -> new PolicySearchEvalSet("d", Map.of(), profiles,
                List.of(new SearchCase("c", CaseType.UNRELATED, "p", "q", List.of(PolicyKey.parse("KINFA:x")), null, null))))
                .hasMessageContaining("정답을 두지 않는다");
        assertThatThrownBy(() -> new Profile("NOWHERE", 25, 1L, null)).hasMessageContaining("지역 코드");
    }

    @Test
    void 스냅샷에_없는_정답_키를_찾는다() {
        Map<String, Profile> profiles = Map.of("p", new Profile("SEOUL", 25, 1L, null));
        PolicyKey known = PolicyKey.parse("KINFA:k");
        PolicySearchEvalSet evalSet = new PolicySearchEvalSet("d", Map.of(), profiles, List.of(
                new SearchCase("a", CaseType.POLICY_NAME, "p", "q", List.of(known, PolicyKey.parse("KINFA:gone")), null, null)));

        assertThat(evalSet.unknownKeys(List.of(known))).containsExactly("a → KINFA:gone");
    }
}
