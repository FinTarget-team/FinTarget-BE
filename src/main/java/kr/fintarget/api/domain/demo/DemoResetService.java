package kr.fintarget.api.domain.demo;

import kr.fintarget.api.domain.ai.chat.repository.AiConversationMessageRepository;
import kr.fintarget.api.domain.ai.chat.repository.AiConversationRepository;
import kr.fintarget.api.domain.ai.usage.AiDailyUsageRepository;
import kr.fintarget.api.domain.demo.dto.DemoResetCounts;
import kr.fintarget.api.domain.demo.dto.DemoUserSummary;
import kr.fintarget.api.domain.expense.repository.ExpenseRepository;
import kr.fintarget.api.domain.goal.repository.GoalRepository;
import kr.fintarget.api.domain.nudge.repository.NudgeRepository;
import kr.fintarget.api.domain.onboarding.repository.OnboardingAnswerRepository;
import kr.fintarget.api.domain.policy.recommend.rerank.PolicyRerankCache;
import kr.fintarget.api.domain.policy.repository.UserPolicyRepository;
import kr.fintarget.api.domain.simulation.repository.SimulationRepository;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 부스 시연용 계정 초기화 (#239). 내 계정 초기화({@link DemoResetController})와 관리자 초기화({@link AdminDemoController})가
 * 이 로직을 공유한다.
 *
 * <p>계정 자체(사용자 행, 소셜 로그인 연결, 발급된 토큰)는 건드리지 않는다. 방문자가 바뀔 때마다 팀원 계정으로 로그인한
 * 상태를 유지한 채 온보딩 1단계부터 다시 시작하는 것이 목적이다. 지우는 데이터는 회원 탈퇴
 * ({@link kr.fintarget.api.domain.user.service.UserService#withdraw})와 같고, 사용자 행만 남긴다.
 *
 * <p>DB 작업은 한 트랜잭션으로 처리한다. 리랭킹 메모리 캐시는 커밋 여부와 무관하게 비우며(캐시가 비는 것은 손해가 아님),
 * 세대를 올려 두므로 진행 중인 미리 계산의 결과도 초기화 뒤에 저장되지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoResetService {

    private final UserRepository userRepository;
    private final SimulationRepository simulationRepository;
    private final GoalRepository goalRepository;
    private final UserPolicyRepository userPolicyRepository;
    private final ExpenseRepository expenseRepository;
    private final OnboardingAnswerRepository onboardingAnswerRepository;
    private final NudgeRepository nudgeRepository;
    private final AiConversationMessageRepository aiConversationMessageRepository;
    private final AiConversationRepository aiConversationRepository;
    private final AiDailyUsageRepository aiDailyUsageRepository;
    private final PolicyRerankCache rerankCache;

    /**
     * 사용자 데이터를 모두 지우고 온보딩을 미완료로 되돌린다. 계정·로그인 토큰은 그대로여서 같은 토큰으로 계속 호출할 수 있다.
     *
     * @throws IllegalArgumentException 사용자가 없을 때 (400)
     */
    @Transactional
    public DemoResetCounts reset(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        UUID uid = UUID.fromString(userId);

        // 외래키 제약 순서: 시뮬레이션 → 목표/정책 → 지출 (회원 탈퇴와 같은 순서)
        long simulations = simulationRepository.deleteByUserId(uid);
        long goals = goalRepository.deleteByUserId(uid);
        long savedPolicies = userPolicyRepository.deleteByUserId(uid);
        long expenses = expenseRepository.deleteByUserId(uid);
        long onboardingAnswers = onboardingAnswerRepository.deleteByUserId(uid);
        long nudges = nudgeRepository.deleteByUserId(userId);
        long aiConversationMessages = aiConversationMessageRepository.deleteOfUserConversations(uid);
        long aiConversations = aiConversationRepository.deleteByUserId(uid);
        long aiUsages = aiDailyUsageRepository.deleteByUserId(userId);

        user.resetForDemo();
        userRepository.save(user);

        // 비운 뒤 세대가 올라가, 지금 돌고 있는 미리 계산이 끝나도 그 결과는 캐시에 들어가지 않는다
        rerankCache.evict(userId);

        DemoResetCounts counts = new DemoResetCounts(onboardingAnswers, goals, simulations, expenses, savedPolicies,
                aiConversations, aiConversationMessages, aiUsages, nudges);
        log.info("시연 계정 초기화 userId={} deleted={}", userId, counts);
        return counts;
    }

    /** 비상용 사용자 목록. 민감 정보(이메일·소셜 ID·토큰)는 넣지 않는다 */
    @Transactional(readOnly = true)
    public List<DemoUserSummary> users() {
        return userRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt")).stream()
                .map(user -> new DemoUserSummary(user.getId(), user.getName(), user.getProvider().name(),
                        user.isOnboardingCompleted()))
                .toList();
    }
}
