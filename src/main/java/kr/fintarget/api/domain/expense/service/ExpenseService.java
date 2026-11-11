package kr.fintarget.api.domain.expense.service;

import kr.fintarget.api.domain.expense.dto.BankTransactionRequest;
import kr.fintarget.api.domain.expense.dto.BankTransactionResult;
import kr.fintarget.api.domain.expense.dto.ExpenseCategoryStat;
import kr.fintarget.api.domain.expense.dto.ExpenseResponse;
import kr.fintarget.api.domain.expense.dto.ExpenseStatsResponse;
import kr.fintarget.api.domain.expense.dto.ExpenseSyncRequest;
import kr.fintarget.api.domain.expense.entity.Expense;
import kr.fintarget.api.domain.expense.event.ExpenseCreatedEvent;
import kr.fintarget.api.domain.expense.repository.ExpenseRepository;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void syncExpenses(UUID userId, List<ExpenseSyncRequest> requests) {
        if (requests.isEmpty()) {
            return;
        }

        LocalDate minDate = requests.stream().map(ExpenseSyncRequest::spentAt).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate maxDate = requests.stream().map(ExpenseSyncRequest::spentAt).max(Comparator.naturalOrder()).orElseThrow();

        Set<ExpenseKey> seen = expenseRepository.findByUserIdAndSpentAtBetween(userId, minDate, maxDate)
            .stream()
            .map(ExpenseKey::from)
            .collect(Collectors.toCollection(HashSet::new));

        for (ExpenseSyncRequest request : requests) {
            if (seen.add(ExpenseKey.from(request))) {
                expenseRepository.insertIfNotExists(
                    UUID.randomUUID(),
                    userId,
                    request.category(),
                    request.amount(),
                    request.description(),
                    request.spentAt(),
                    LocalDateTime.now()
                );
            }
        }
    }

    /**
     * 가상 금융 서버(mock-bank)가 보낸 거래를 지출로 저장하고, 거래별로 새로 저장됐는지(CREATED) 중복이라 건너뛰었는지(DUPLICATE) 돌려준다.
     * 중복 판정은 syncExpenses와 같은 사용자별 (카테고리, 금액, 날짜, 설명) 조합이다.
     * 존재하지 않는 사용자가 하나라도 있으면 아무것도 저장하지 않고 거부한다 (expense.user_id에 FK가 없어 그대로 저장되기 때문).
     * 새로 저장된 지출은 사용자별로 묶어 ExpenseCreatedEvent로 발행하고, 커밋 뒤 앱에 실시간으로 전달된다.
     */
    @Transactional
    public List<BankTransactionResult> receiveBankTransactions(List<BankTransactionRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        validateUsersExist(requests);

        Map<UUID, Set<ExpenseKey>> seenByUser = new HashMap<>();
        // 요청에 처음 나온 사용자 순서대로 이벤트를 발행하도록 LinkedHashMap을 쓴다
        Map<UUID, List<ExpenseResponse>> createdByUser = new LinkedHashMap<>();
        List<BankTransactionResult> results = new ArrayList<>();
        for (BankTransactionRequest request : requests) {
            Set<ExpenseKey> seen = seenByUser.computeIfAbsent(request.userId(), userId -> loadExistingKeys(userId, requests));
            UUID expenseId = UUID.randomUUID();
            LocalDateTime createdAt = LocalDateTime.now();
            // 조회 이후 다른 요청이 먼저 넣은 경우도 insertIfNotExists가 0을 반환하므로 DUPLICATE로 처리된다
            boolean created = seen.add(ExpenseKey.from(request))
                    && expenseRepository.insertIfNotExists(
                            expenseId,
                            request.userId(),
                            request.category(),
                            request.amount(),
                            request.description(),
                            request.spentAt(),
                            createdAt) > 0;
            if (created) {
                createdByUser.computeIfAbsent(request.userId(), userId -> new ArrayList<>())
                        .add(ExpenseResponse.of(expenseId, request.amount(), request.category(), request.description(),
                                request.spentAt(), createdAt));
            }
            results.add(new BankTransactionResult(request.transactionId(),
                    created ? BankTransactionResult.Result.CREATED : BankTransactionResult.Result.DUPLICATE));
        }
        createdByUser.forEach((userId, expenses) -> eventPublisher.publishEvent(new ExpenseCreatedEvent(userId, expenses)));
        return results;
    }

    private void validateUsersExist(List<BankTransactionRequest> requests) {
        Set<String> requestedIds = requests.stream()
                .map(request -> request.userId().toString())
                .collect(Collectors.toSet());
        Set<String> existingIds = userRepository.findAllById(requestedIds).stream()
                .map(User::getId)
                .collect(Collectors.toSet());
        requestedIds.stream()
                .filter(id -> !existingIds.contains(id))
                .findFirst()
                .ifPresent(id -> {
                    throw new IllegalArgumentException("존재하지 않는 사용자입니다: " + id);
                });
    }

    private Set<ExpenseKey> loadExistingKeys(UUID userId, List<BankTransactionRequest> requests) {
        List<LocalDate> dates = requests.stream()
                .filter(request -> request.userId().equals(userId))
                .map(BankTransactionRequest::spentAt)
                .toList();
        LocalDate minDate = dates.stream().min(Comparator.naturalOrder()).orElseThrow();
        LocalDate maxDate = dates.stream().max(Comparator.naturalOrder()).orElseThrow();
        return expenseRepository.findByUserIdAndSpentAtBetween(userId, minDate, maxDate)
            .stream()
            .map(ExpenseKey::from)
            .collect(Collectors.toCollection(HashSet::new));
    }

    private record ExpenseKey(String category, Long amount, LocalDate spentAt, String description) {
        static ExpenseKey from(Expense expense) {
            return new ExpenseKey(expense.getCategory(), expense.getAmount(), expense.getSpentAt(), expense.getDescription());
        }

        static ExpenseKey from(ExpenseSyncRequest request) {
            return new ExpenseKey(request.category(), request.amount(), request.spentAt(), request.description());
        }

        static ExpenseKey from(BankTransactionRequest request) {
            return new ExpenseKey(request.category(), request.amount(), request.spentAt(), request.description());
        }
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpenses(UUID userId) {
        return expenseRepository.findByUserId(userId)
            .stream()
            .map(ExpenseResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpensesByPeriod(UUID userId, LocalDate start, LocalDate end) {
        validatePeriod(start, end);
        return expenseRepository.findByUserIdAndSpentAtBetween(userId, start, end)
            .stream()
            .map(ExpenseResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExpenseStatsResponse getExpenseStats(UUID userId, LocalDate start, LocalDate end) {
        validatePeriod(start, end);

        List<ExpenseCategoryStat> byCategory = expenseRepository.sumByCategory(userId, start, end);
        long totalAmount = byCategory.stream().mapToLong(ExpenseCategoryStat::amount).sum();
        return new ExpenseStatsResponse(totalAmount, byCategory);
    }

    private static void validatePeriod(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("시작일이 종료일보다 늦을 수 없습니다.");
        }
    }
}
