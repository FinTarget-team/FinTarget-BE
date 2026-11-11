package kr.fintarget.api.domain.user.service;

import kr.fintarget.api.domain.ai.chat.entity.AiConversation;
import kr.fintarget.api.domain.ai.chat.entity.AiConversationMessage;
import kr.fintarget.api.domain.ai.chat.repository.AiConversationMessageRepository;
import kr.fintarget.api.domain.ai.chat.repository.AiConversationRepository;
import kr.fintarget.api.domain.expense.entity.Expense;
import kr.fintarget.api.domain.expense.repository.ExpenseRepository;
import kr.fintarget.api.domain.goal.entity.Goal;
import kr.fintarget.api.domain.goal.repository.GoalRepository;
import kr.fintarget.api.domain.nudge.entity.Nudge;
import kr.fintarget.api.domain.nudge.repository.NudgeRepository;
import kr.fintarget.api.domain.onboarding.entity.OnboardingAnswer;
import kr.fintarget.api.domain.onboarding.repository.OnboardingAnswerRepository;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
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
 * 실제 DB(H2)에서 탈퇴한 사용자의 데이터가 모든 테이블에서 지워지고, 다른 사용자 데이터는 남는지 확인한다.
 */
@SpringBootTest
class UserWithdrawIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    private String withdrawnUserId;
    private String otherUserId;

    @BeforeEach
    void setUp() {
        withdrawnUserId = createUserWithData();
        otherUserId = createUserWithData();
    }

    @AfterEach
    void tearDown() {
        userService.withdraw(otherUserId);
    }

    private String createUserWithData() {
        User user = userRepository.save(User.builder()
                .provider(SocialProvider.KAKAO)
                .providerId("kakao-" + UUID.randomUUID())
                .build());
        UUID uid = UUID.fromString(user.getId());
        goalRepository.save(new Goal(uid, "전세 자금", 30_000_000L, 0L, LocalDate.now().plusYears(1)));
        expenseRepository.save(new Expense(uid, 10_000L, "식비", "점심", LocalDate.now()));
        onboardingAnswerRepository.save(new OnboardingAnswer(uid, 1, "1999"));
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

    private static final String[] USER_TABLES = {
            "goal", "expense", "onboarding_answer", "nudges", "ai_conversation", "ai_conversation_message", "ai_daily_usage"
    };

    @Test
    void 탈퇴하면_사용자와_연관된_모든_데이터가_삭제된다() {
        userService.withdraw(withdrawnUserId);

        assertThat(userRepository.existsById(withdrawnUserId)).isFalse();
        for (String table : USER_TABLES) {
            assertThat(count(table, withdrawnUserId)).as(table).isZero();
        }
    }

    @Test
    void 탈퇴해도_다른_사용자의_데이터는_남는다() {
        userService.withdraw(withdrawnUserId);

        assertThat(userRepository.existsById(otherUserId)).isTrue();
        for (String table : USER_TABLES) {
            assertThat(count(table, otherUserId)).as(table).isEqualTo(1);
        }
    }
}
