package kr.fintarget.api.domain.policy.search.eval;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.fintarget.api.domain.user.code.Region;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Year;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 정책 검색 평가셋 (#184 1단계). 형식 설명은 파일의 guide 항목 참고.
 *
 * @param guide 필드 설명 (문서용, 코드에서 쓰지 않음)
 */
public record PolicySearchEvalSet(String description, Map<String, String> guide, Map<String, Profile> profiles,
                                  List<SearchCase> cases) {

    public static final String RESOURCE = "ai-eval/policy-search-cases.json";

    public PolicySearchEvalSet {
        profiles = Map.copyOf(profiles);
        cases = List.copyOf(cases);
        Set<String> ids = new HashSet<>();
        for (SearchCase c : cases) {
            if (!ids.add(c.id())) {
                throw new IllegalStateException("문항 ID 중복: " + c.id());
            }
            if (!profiles.containsKey(c.profile())) {
                throw new IllegalStateException("없는 프로필: " + c.id() + " → " + c.profile());
            }
            if (!c.type().scoredForRecall() && !c.relevant().isEmpty()) {
                throw new IllegalStateException(c.type() + " 문항은 정답을 두지 않는다: " + c.id());
            }
        }
    }

    public static PolicySearchEvalSet load() {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return mapper.readValue(in, PolicySearchEvalSet.class);
        } catch (IOException e) {
            throw new UncheckedIOException("평가셋을 읽을 수 없습니다: " + RESOURCE, e);
        }
    }

    public Profile profileOf(SearchCase c) {
        return profiles.get(c.profile());
    }

    /** 정답에 스냅샷에 없는 정책 키가 있으면 그 목록 (라벨링 후 스냅샷을 바꿨을 때 확인용) */
    public List<String> unknownKeys(Collection<PolicyKey> snapshotKeys) {
        Set<PolicyKey> known = Set.copyOf(snapshotKeys);
        return cases.stream()
                .flatMap(c -> c.relevant().stream().filter(key -> !known.contains(key)).map(key -> c.id() + " → " + key))
                .toList();
    }

    public enum CaseType {
        POLICY_NAME(true),
        BENEFIT_SITUATION(true),
        REGION(true),
        LOAN(true),
        UNRELATED(false),
        AMBIGUOUS(false);

        private final boolean scoredForRecall;

        CaseType(boolean scoredForRecall) {
            this.scoredForRecall = scoredForRecall;
        }

        /** Recall@k·MRR 계산 대상인지. 무관·모호 문항은 정답이 없어 제외한다 */
        public boolean scoredForRecall() {
            return scoredForRecall;
        }
    }

    /**
     * @param relevant   정답 정책 키. 그 프로필에서 신청할 수 있는 정책만
     * @param loanIntent 대출을 찾는 질문이면 true (KINFA 쏠림 판정 제외). null이면 type이 LOAN일 때 true
     * @param labeled    라벨링을 마쳤는지. 생략하면 false(라벨링 전).
     *                   {@code relevant}가 비었는지로 판정하면 "라벨링 전"과 "정답 없음이 맞는 문항"이
     *                   구분되지 않아(예: region-04) 정답 0건 문항이 채점에서 빠진다
     */
    public record SearchCase(String id, CaseType type, String profile, String question, List<PolicyKey> relevant,
                             Boolean loanIntent, Boolean labeled, String note) {

        public SearchCase {
            relevant = relevant == null ? List.of() : List.copyOf(relevant);
            labeled = labeled != null && labeled;
            if (new HashSet<>(relevant).size() != relevant.size()) {
                throw new IllegalStateException("정답 정책 키 중복: " + id);
            }
            if (!labeled && !relevant.isEmpty()) {
                throw new IllegalStateException("정답을 넣었으면 labeled를 true로 둬야 합니다: " + id);
            }
        }

        public boolean isLoanIntent() {
            return loanIntent != null ? loanIntent : type == CaseType.LOAN;
        }

        /** 정답이 없는 것이 맞다고 라벨한 문항. Recall·MRR 대신 "결과 0건이면 통과"로 채점한다 */
        public boolean labeledEmpty() {
            return labeled && relevant.isEmpty();
        }

        /** Recall·MRR 채점 대상 (라벨링을 마쳤고 정답이 1건 이상) */
        public boolean hasRelevant() {
            return labeled && !relevant.isEmpty();
        }
    }

    /**
     * 평가용 사용자 조건. 나이는 출생연도 대신 만 나이로 적어 해가 바뀌어도 구간이 같게 한다:
     * 출생연도 = 평가 연도 - age 로 넣으면 User.ageRange()는 [age - 1, age]다 (생일 전후).
     *
     * @param age    만 나이. null이면 나이 정보 없음
     * @param income 세전 월 소득(원). null이면 소득 정보 없음
     */
    public record Profile(String region, Integer age, Long income, String note) {

        public Profile {
            if (region != null && Region.fromCode(region) == null) {
                throw new IllegalStateException("지역 코드가 아닙니다: " + region);
            }
        }

        public User toUser(String userId, Year evalYear) {
            return User.builder().id(userId).provider(SocialProvider.KAKAO).providerId("eval-" + userId)
                    .birthYear(age == null ? null : evalYear.getValue() - age)
                    .income(income).region(region).build();
        }
    }
}
