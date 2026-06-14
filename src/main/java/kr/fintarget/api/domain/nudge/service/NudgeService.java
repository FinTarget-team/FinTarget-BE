package kr.fintarget.api.domain.nudge.service;

import kr.fintarget.api.domain.nudge.dto.NudgeResponse;
import kr.fintarget.api.domain.nudge.entity.Nudge;
import kr.fintarget.api.domain.nudge.repository.NudgeRepository;
import kr.fintarget.api.expense.entity.Expense;
import kr.fintarget.api.expense.repository.ExpenseRepository;
import kr.fintarget.api.goal.entity.Goal;
import kr.fintarget.api.goal.repository.GoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NudgeService {

    private static final String TYPE_GOAL_DEADLINE = "GOAL_DEADLINE";
    private static final String TYPE_UNUSUAL_SPENDING = "UNUSUAL_SPENDING";
    private static final double UNUSUAL_SPENDING_RATIO = 1.5; // 평소 평균보다 50% 이상 증가 시 알림

    private final NudgeRepository nudgeRepository;
    private final GoalRepository goalRepository;
    private final ExpenseRepository expenseRepository;

    @Transactional
    public Map<String, Object> getNudges(String userId) {
        generateGoalDeadlineNudge(userId);
        generateUnusualSpendingNudge(userId);

        List<Nudge> nudges = nudgeRepository.findByUserIdOrderByCreatedAtDesc(userId);
        long unreadCount = nudgeRepository.countByUserIdAndIsRead(userId, false);
        return Map.of(
                "nudges", nudges.stream().map(NudgeResponse::from).collect(Collectors.toList()),
                "unreadCount", unreadCount
        );
    }

    @Transactional
    public void markAsRead(String userId, String nudgeId) {
        Nudge nudge = nudgeRepository.findById(nudgeId)
                .orElseThrow(() -> new IllegalArgumentException("넛지를 찾을 수 없습니다."));
        if (!nudge.getUserId().equals(userId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }
        nudge.markAsRead();
    }

    /**
     * 목표 마감 D-30 이내면 하루 한 번 알림 생성
     */
    private void generateGoalDeadlineNudge(String userId) {
        UUID uid = UUID.fromString(userId);
        Goal goal = goalRepository.findByUserId(uid).orElse(null);
        if (goal == null || goal.getStatus() != Goal.GoalStatus.ACTIVE) return;

        long dDay = ChronoUnit.DAYS.between(LocalDate.now(), goal.getDeadline());
        if (dDay < 0 || dDay > 30) return;

        if (alreadyGeneratedToday(userId, TYPE_GOAL_DEADLINE)) return;

        Nudge nudge = Nudge.builder()
                .userId(userId)
                .type(TYPE_GOAL_DEADLINE)
                .message(String.format("목표 '%s' 마감까지 D-%d일 남았습니다.", goal.getTitle(), dDay))
                .build();

        nudgeRepository.save(nudge);
    }

    /**
     * 이번달 카테고리별 지출이 평소(과거 달 평균)보다 50% 이상 늘었으면 알림
     */
    private void generateUnusualSpendingNudge(String userId) {
        UUID uid = UUID.fromString(userId);
        YearMonth currentMonth = YearMonth.now();
        LocalDate monthStart = currentMonth.atDay(1);
        LocalDate monthEnd = currentMonth.atEndOfMonth();

        List<Expense> allExpenses = expenseRepository.findByUserId(uid);
        if (allExpenses.isEmpty()) return;

        // 이번달 카테고리별 합계
        Map<String, Long> currentMonthByCategory = allExpenses.stream()
                .filter(e -> !e.getSpentAt().isBefore(monthStart) && !e.getSpentAt().isAfter(monthEnd))
                .collect(Collectors.groupingBy(Expense::getCategory, Collectors.summingLong(Expense::getAmount)));

        if (currentMonthByCategory.isEmpty()) return;

        // 과거 달(이번달 제외) 카테고리별 총합 / 달 개수 → 월평균
        Map<String, Long> pastTotalByCategory = new HashMap<>();
        Map<String, Set<String>> pastMonthsByCategory = new HashMap<>();
        for (Expense e : allExpenses) {
            if (e.getSpentAt().isBefore(monthStart)) {
                pastTotalByCategory.merge(e.getCategory(), e.getAmount(), Long::sum);
                pastMonthsByCategory.computeIfAbsent(e.getCategory(), k -> new HashSet<>())
                        .add(YearMonth.from(e.getSpentAt()).toString());
            }
        }

        for (Map.Entry<String, Long> entry : currentMonthByCategory.entrySet()) {
            String category = entry.getKey();
            long currentTotal = entry.getValue();

            Long pastTotal = pastTotalByCategory.get(category);
            Set<String> pastMonths = pastMonthsByCategory.get(category);
            if (pastTotal == null || pastMonths == null || pastMonths.isEmpty()) continue;

            double avg = (double) pastTotal / pastMonths.size();
            if (avg <= 0) continue;

            if (currentTotal >= avg * UNUSUAL_SPENDING_RATIO) {
                if (alreadyGeneratedTodayForCategory(userId, TYPE_UNUSUAL_SPENDING, category)) continue;

                long increasePercent = Math.round((currentTotal - avg) / avg * 100);

                Nudge nudge = Nudge.builder()
                        .userId(userId)
                        .type(TYPE_UNUSUAL_SPENDING)
                        .relatedCategory(category)
                        .message(String.format("이번달 '%s' 지출이 평소보다 %d%% 늘었어요. (평소 %,d원 → 이번달 %,d원)",
                                category, increasePercent, (long) avg, currentTotal))
                        .build();

                nudgeRepository.save(nudge);
            }
        }
    }

    private boolean alreadyGeneratedToday(String userId, String type) {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        return nudgeRepository.existsByUserIdAndTypeAndCreatedAtAfter(userId, type, startOfToday);
    }

    private boolean alreadyGeneratedTodayForCategory(String userId, String type, String category) {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        return nudgeRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .anyMatch(n -> n.getType().equals(type)
                        && category.equals(n.getRelatedCategory())
                        && n.getCreatedAt().isAfter(startOfToday));
    }
}