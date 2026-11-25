package kr.fintarget.api.domain.demo;

import kr.fintarget.api.domain.ai.chat.entity.AiConversation;
import kr.fintarget.api.domain.ai.chat.entity.AiConversationMessage;
import kr.fintarget.api.domain.ai.chat.repository.AiConversationMessageRepository;
import kr.fintarget.api.domain.ai.chat.repository.AiConversationRepository;
import kr.fintarget.api.domain.demo.dto.DemoResetResult;
import kr.fintarget.api.domain.expense.entity.Expense;
import kr.fintarget.api.domain.expense.repository.ExpenseRepository;
import kr.fintarget.api.domain.goal.entity.Goal;
import kr.fintarget.api.domain.goal.repository.GoalRepository;
import kr.fintarget.api.domain.nudge.entity.Nudge;
import kr.fintarget.api.domain.nudge.repository.NudgeRepository;
import kr.fintarget.api.domain.onboarding.entity.OnboardingAnswer;
import kr.fintarget.api.domain.onboarding.repository.OnboardingAnswerRepository;
import kr.fintarget.api.domain.policy.recommend.rerank.PolicyRerankCache;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.dto.UserProfileResponse;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import kr.fintarget.api.domain.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 DB(H2)에서 시연 초기화가 지정 사용자의 데이터만 지우고, 계정은 남기고 온보딩을 1단계로 되돌리는지 확인한다 (#239).
 */
@SpringBootTest
class DemoResetIntegrationTest {

    @Autowired
    private DemoResetService demoResetService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GoalRepository goalRepository;
    @Autowired
    private ExpenseRepository expenseRepository;
    @Autowired
    private OnboardingAnswerRepository onboardingAnswerRepository;
    @Autowired
    private NudgeRepository nudgeRepository;
    @Autowired
    private AiConversationRepository aiConversationRepository;
    @Autowired
    private AiConversationMessageRepository aiConversationMessageRepository;
    @Autowired
    private PolicyRerankCache rerankCache;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 초기화로 비워지는 표. 지출(expense, #239)과 넛지(nudges, #253)는 남기므로 넣지 않는다
    private static final String[] CLEARED_TABLES = {
            "goal", "onboarding_answer", "ai_conversation", "ai_conversation_message", "ai_daily_usage"
    };

    private String resetUserId;
    private String otherUserId;

    @BeforeEach
    void setUp() {
        resetUserId = createOnboardedUserWithData();
        otherUserId = createOnboardedUserWithData();
    }

    @AfterEach
    void tearDown() {
        userService.withdraw(resetUserId);
        userService.withdraw(otherUserId);
    }

    private String createOnboardedUserWithData() {
        User user = userRepository.save(User.builder()
                .provider(SocialProvider.KAKAO)
                .providerId("kakao-" + UUID.randomUUID())
                .name("지원")
                .birthYear(2000)
                .income(3_000_000L)
                .employmentType("EMPLOYED")
                .region("SEOUL")
                .housingStatus("HOMELESS")
                .onboardingCompleted(true)
                .build());
        UUID uid = UUID.fromString(user.getId());
        goalRepository.save(new Goal(uid, "전세 자금", 30_000_000L, 0L, LocalDate.now().plusYears(1)));
        expenseRepository.save(new Expense(uid, 10_000L, "식비", "점심", LocalDate.now()));
        onboardingAnswerRepository.save(new OnboardingAnswer(uid, 1, "지원"));
        onboardingAnswerRepository.save(new OnboardingAnswer(uid, 2, "2000"));
        nudgeRepository.save(Nudge.builder().userId(user.getId()).type("SAVING").message("이번 주 식비를 줄여 보세요").build());
        AiConversation conversation = aiConversationRepository.save(
                AiConversation.start(uid, UUID.randomUUID(), LocalDateTime.now(), Duration.ofHours(1)));
        aiConversationMessageRepository.save(new AiConversationMessage(
                conversation.getConversationId(), 1, AiConversationMessage.Role.USER, "청년 월세 지원 조건이 뭐야?", LocalDateTime.now()));
        jdbcTemplate.update("insert into ai_daily_usage (usage_id, user_id, usage_date, feature, used_count) values (?, ?, ?, 'POLICY_RAG', 3)",
                UUID.randomUUID(), user.getId(), LocalDate.now());
        return user.getId();
    }

    private int count(String table, String userId) {
        String column = "ai_conversation_message".equals(table)
                ? "conversation_id in (select conversation_id from ai_conversation where cast(user_id as varchar) = ?)"
                : "cast(user_id as varchar) = ?";
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where " + column, Integer.class, userId);
    }

    @Test
    void 초기화하면_지출을_뺀_사용자_데이터가_모두_지워진다() {
        demoResetService.reset(resetUserId);

        for (String table : CLEARED_TABLES) {
            assertThat(count(table, resetUserId)).as(table).isZero();
        }
    }

    @Test
    void 초기화해도_지출은_그대로_남는다() {
        demoResetService.reset(resetUserId);

        assertThat(count("expense", resetUserId)).isEqualTo(1);
        assertThat(expenseRepository.findByUserId(UUID.fromString(resetUserId))).hasSize(1);
    }

    @Test
    void 초기화해도_계정과_소셜_로그인_연결은_남는다() {
        User before = userRepository.findById(resetUserId).orElseThrow();
        String providerId = before.getProviderId();

        demoResetService.reset(resetUserId);

        User after = userRepository.findById(resetUserId).orElseThrow();
        assertThat(after.getId()).isEqualTo(resetUserId);
        assertThat(after.getProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(after.getProviderId()).isEqualTo(providerId);
    }

    @Test
    void 초기화하면_프로필이_비고_온보딩이_미완료가_된다() {
        demoResetService.reset(resetUserId);

        UserProfileResponse profile = userService.getProfile(resetUserId);
        assertThat(profile.isOnboardingCompleted()).isFalse();
        assertThat(profile.getName()).isNull();
        assertThat(profile.getBirthYear()).isNull();
        assertThat(profile.getEmploymentType()).isNull();
        assertThat(profile.getIncomeBracket()).isNull();
        assertThat(profile.getRegion()).isNull();
        // 주택 보유 여부는 미응답도 NOT_SPECIFIED로 내려간다 (null로 내려가지 않음)
        assertThat(profile.getHousingStatus()).isEqualTo("NOT_SPECIFIED");
    }

    @Test
    void 초기화하면_온보딩_답변이_없어_1단계부터_다시_시작한다() {
        demoResetService.reset(resetUserId);

        assertThat(onboardingAnswerRepository.findByUserIdOrderByStepAsc(UUID.fromString(resetUserId))).isEmpty();
    }

    @Test
    void 초기화해도_다른_사용자의_데이터와_프로필은_그대로다() {
        demoResetService.reset(resetUserId);

        for (String table : CLEARED_TABLES) {
            assertThat(count(table, otherUserId)).as(table).isPositive();
        }
        assertThat(count("expense", otherUserId)).isEqualTo(1);
        assertThat(count("nudges", otherUserId)).isEqualTo(1);
        UserProfileResponse other = userService.getProfile(otherUserId);
        assertThat(other.isOnboardingCompleted()).isTrue();
        assertThat(other.getName()).isEqualTo("지원");
        assertThat(other.getRegion()).isEqualTo("SEOUL");
    }

    @Test
    void 응답에_지운_항목별_건수와_남긴_지출_넛지_건수가_담긴다() {
        DemoResetResult result = demoResetService.reset(resetUserId);

        assertThat(result.deleted().onboardingAnswers()).isEqualTo(2);
        assertThat(result.deleted().goals()).isEqualTo(1);
        assertThat(result.deleted().aiConversations()).isEqualTo(1);
        assertThat(result.deleted().aiConversationMessages()).isEqualTo(1);
        assertThat(result.deleted().aiUsages()).isEqualTo(1);
        assertThat(result.deleted().simulations()).isZero();
        assertThat(result.deleted().savedPolicies()).isZero();
        assertThat(result.keptExpenses()).isEqualTo(1);
        // 넛지·알림은 지우지 않고 남긴 건수로 알린다 (#253)
        assertThat(result.keptNudges()).isEqualTo(1);
        assertThat(nudgeRepository.findByUserIdOrderByCreatedAtDesc(resetUserId))
                .singleElement().satisfies(nudge -> assertThat(nudge.getMessage()).isEqualTo("이번 주 식비를 줄여 보세요"));
    }

    @Test
    void 두_번_연속_초기화해도_오류가_없고_지운_건수가_0이다() {
        demoResetService.reset(resetUserId);

        DemoResetResult second = demoResetService.reset(resetUserId);
        assertThat(second.deleted().onboardingAnswers()).isZero();
        assertThat(second.deleted().goals()).isZero();
        // 지출·넛지는 지우지 않으므로 두 번째에도 그대로 세어진다
        assertThat(second.keptExpenses()).isEqualTo(1);
        assertThat(second.keptNudges()).isEqualTo(1);
        assertThat(userRepository.existsById(resetUserId)).isTrue();
    }

    @Test
    void 없는_사용자를_초기화하면_거부한다() {
        String missingId = UUID.randomUUID().toString();

        assertThat(userRepository.existsById(missingId)).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> demoResetService.reset(missingId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }

    @Test
    void 초기화하면_리랭킹_캐시가_비고_진행_중인_계산_결과도_저장되지_않는다() {
        PolicyRerankCache.Entry entry = new PolicyRerankCache.Entry(java.util.List.of(UUID.randomUUID()),
                java.util.List.of("이유"), "hash-1", "policy-rerank-v1", "gpt-6.1-sol", LocalDateTime.now());
        rerankCache.put(resetUserId, entry);
        // 초기화 전에 시작된 미리 계산이 들고 있는 세대
        long generationBeforeReset = rerankCache.generation(resetUserId);

        demoResetService.reset(resetUserId);

        assertThat(rerankCache.find(resetUserId, "hash-1")).isEmpty();
        assertThat(rerankCache.putIfUnchanged(resetUserId, generationBeforeReset, entry)).isFalse();
        assertThat(rerankCache.find(resetUserId, "hash-1")).isEmpty();
    }

    @Test
    void 초기화해도_다른_사용자의_리랭킹_캐시는_남는다() {
        PolicyRerankCache.Entry entry = new PolicyRerankCache.Entry(java.util.List.of(UUID.randomUUID()),
                java.util.List.of("이유"), "hash-other", "policy-rerank-v1", "gpt-6.1-sol", LocalDateTime.now());
        rerankCache.put(otherUserId, entry);

        demoResetService.reset(resetUserId);

        assertThat(rerankCache.find(otherUserId, "hash-other")).isPresent();
        rerankCache.evict(otherUserId);
    }

    @Test
    void 사용자_목록에는_민감_정보_없이_네_가지_값만_담긴다() {
        assertThat(demoResetService.users())
                .anySatisfy(summary -> {
                    assertThat(summary.userId()).isEqualTo(resetUserId);
                    assertThat(summary.name()).isEqualTo("지원");
                    assertThat(summary.provider()).isEqualTo("KAKAO");
                    assertThat(summary.onboardingCompleted()).isTrue();
                });
    }
}
