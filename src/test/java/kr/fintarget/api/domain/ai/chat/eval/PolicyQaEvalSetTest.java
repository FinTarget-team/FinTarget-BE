package kr.fintarget.api.domain.ai.chat.eval;

import kr.fintarget.api.domain.ai.chat.eval.PolicyQaEvalSet.Behavior;
import kr.fintarget.api.domain.ai.chat.eval.PolicyQaEvalSet.Category;
import kr.fintarget.api.domain.ai.chat.eval.PolicyQaEvalSet.EvalCase;
import kr.fintarget.api.domain.ai.chat.eval.PolicyQaEvalSet.PolicySnapshot;
import kr.fintarget.api.domain.ai.chat.service.PolicyDocument;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 평가셋 파일 자체를 검증한다. OpenAI를 호출하지 않으므로 일반 테스트와 함께 돈다.
 */
class PolicyQaEvalSetTest {

    // 휴대전화 번호, 이메일, 주민등록번호 형태. 정책 ID(숫자 20자리)는 앞뒤가 숫자라 걸리지 않게 경계를 둔다
    private static final List<Pattern> PERSONAL_INFO_PATTERNS = List.of(
            Pattern.compile("(?<!\\d)01[016789][-. ]?\\d{3,4}[-. ]?\\d{4}(?!\\d)"),
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
            Pattern.compile("(?<!\\d)\\d{6}-?[1-4]\\d{6}(?!\\d)"));

    private final PolicyQaEvalSet evalSet = PolicyQaEvalSet.load();

    @Test
    void 정책_15건_이상과_문항_50개_내외로_구성한다() {
        assertThat(evalSet.policies()).hasSizeBetween(15, 16);
        assertThat(evalSet.cases()).hasSizeBetween(45, 55);
        assertThat(evalSet.cases().stream().map(EvalCase::id).collect(Collectors.toSet()))
                .hasSameSizeAs(evalSet.cases());
    }

    @Test
    void 소스와_정책_유형과_원문_부족_정책을_모두_포함한다() {
        Map<String, Long> bySourceAndType = evalSet.policies().values().stream()
                .collect(Collectors.groupingBy(p -> p.source() + "/" + p.policyType(), Collectors.counting()));
        assertThat(bySourceAndType).containsOnlyKeys(
                "YOUTH_CENTER/HOUSING", "YOUTH_CENTER/FINANCE_WELFARE", "KINFA/FINANCE_WELFARE", "KINFA/STARTUP");

        long limited = evalSet.policies().values().stream()
                .map(PolicySnapshot::toPolicy).map(PolicyDocument::of).filter(PolicyDocument::sourceLimited).count();
        assertThat(limited).isGreaterThanOrEqualTo(2);
    }

    @Test
    void 원문에_없는_내용_자격_판단_규칙_무시_유형을_충분히_포함한다() {
        Map<Category, Long> byCategory = evalSet.cases().stream()
                .collect(Collectors.groupingBy(EvalCase::category, Collectors.counting()));
        assertThat(byCategory.get(Category.NOT_IN_SOURCE)).isGreaterThanOrEqualTo(10);
        assertThat(byCategory.get(Category.ELIGIBILITY) + byCategory.get(Category.PROFILE_CHECK)).isGreaterThanOrEqualTo(12);
        assertThat(byCategory.get(Category.INJECTION)).isGreaterThanOrEqualTo(5);
        assertThat(byCategory).containsKeys(Category.values());
    }

    @Test
    void 정책마다_3_4문항이고_참조하는_정책과_사용자_정보가_있다() {
        Map<String, Long> perPolicy = evalSet.cases().stream()
                .collect(Collectors.groupingBy(EvalCase::policy, Collectors.counting()));
        assertThat(perPolicy.keySet()).isEqualTo(evalSet.policies().keySet());
        assertThat(perPolicy.values()).allSatisfy(count -> assertThat(count).isBetween(3L, 4L));
        assertThat(evalSet.cases()).allSatisfy(c -> assertThat(evalSet.profiles()).containsKey(c.profile()));
    }

    @Test
    void 기대_사실은_정책_원문에_실제로_있다() {
        for (EvalCase evalCase : evalSet.cases()) {
            String source = PolicyQaScorer.normalize(evalSet.documentOf(evalCase).text()
                    + evalCase.history().stream().map(PolicyQaEvalSet.Turn::content).collect(Collectors.joining()));
            for (List<String> group : evalCase.expectedFacts()) {
                assertThat(group.stream().anyMatch(fact -> source.contains(PolicyQaScorer.normalize(fact))))
                        .as("%s 기대 사실 %s가 원문에 없음", evalCase.id(), group)
                        .isTrue();
            }
        }
    }

    @Test
    void 원문에_없는_내용_문항의_금지_표현은_원문에_없다() {
        for (EvalCase evalCase : evalSet.cases()) {
            String source = PolicyQaScorer.normalize(evalSet.documentOf(evalCase).text());
            for (String forbidden : evalCase.mustNotInclude()) {
                assertThat(source).as("%s 금지 표현 %s가 원문에 있음", evalCase.id(), forbidden)
                        .doesNotContain(PolicyQaScorer.normalize(forbidden));
            }
        }
    }

    @Test
    void 거절_유형은_금지_표현이나_단정_검사로_자동_판정할_수_있다() {
        assertThat(evalSet.cases()).filteredOn(c -> c.behavior() == Behavior.REFUSE)
                .allSatisfy(c -> assertThat(!c.mustNotInclude().isEmpty() || c.noDefinitiveEligibility())
                        .as(c.id()).isTrue());
    }

    @Test
    void 정책_원문_스냅샷에_개인정보가_없다() {
        Set<String> found = new HashSet<>();
        for (PolicySnapshot snapshot : evalSet.policies().values()) {
            for (String value : textValues(snapshot)) {
                for (Pattern pattern : PERSONAL_INFO_PATTERNS) {
                    pattern.matcher(value).results().map(m -> snapshot.name() + ": " + m.group()).forEach(found::add);
                }
            }
        }
        assertThat(found).isEmpty();
    }

    private static List<String> textValues(PolicySnapshot snapshot) {
        List<Function<PolicySnapshot, String>> getters = List.of(
                PolicySnapshot::name, PolicySnapshot::description, PolicySnapshot::supportContent,
                PolicySnapshot::eligibilityDetail, PolicySnapshot::exclusionCriteria, PolicySnapshot::incomeCondition,
                PolicySnapshot::ageCondition, PolicySnapshot::applyMethod, PolicySnapshot::screeningMethod,
                PolicySnapshot::requiredDocuments, PolicySnapshot::applyPeriod, PolicySnapshot::businessPeriod,
                PolicySnapshot::referenceUrl, PolicySnapshot::applyUrl, PolicySnapshot::contactInfo,
                PolicySnapshot::etcNote);
        return getters.stream().map(getter -> getter.apply(snapshot)).filter(value -> value != null).toList();
    }
}
