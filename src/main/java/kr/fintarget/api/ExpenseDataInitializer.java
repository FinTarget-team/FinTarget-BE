package kr.fintarget.api;

import kr.fintarget.api.expense.entity.Expense;
import kr.fintarget.api.expense.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
@Order(2)
@RequiredArgsConstructor
public class ExpenseDataInitializer implements ApplicationRunner {

    private final ExpenseRepository expenseRepository;

    private static final UUID PERSONA_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PERSONA_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (expenseRepository.count() > 0) return;

        // 페르소나 1: 절약형 직장인
        save(PERSONA_1, 8000L, "식비", "점심 식사", LocalDate.of(2026, 5, 2));
        save(PERSONA_1, 1500L, "교통", "지하철", LocalDate.of(2026, 5, 3));
        save(PERSONA_1, 45000L, "식비", "장보기", LocalDate.of(2026, 5, 5));
        save(PERSONA_1, 55000L, "통신", "휴대폰 요금", LocalDate.of(2026, 5, 10));
        save(PERSONA_1, 500000L, "주거", "월세", LocalDate.of(2026, 5, 1));
        save(PERSONA_1, 12000L, "식비", "저녁 식사", LocalDate.of(2026, 5, 12));
        save(PERSONA_1, 4500L, "카페", "커피", LocalDate.of(2026, 5, 15));
        save(PERSONA_1, 8000L, "의료", "약국", LocalDate.of(2026, 5, 18));
        save(PERSONA_1, 1200L, "교통", "버스", LocalDate.of(2026, 5, 20));
        save(PERSONA_1, 9000L, "식비", "점심 식사", LocalDate.of(2026, 5, 25));

        // 페르소나 2: 소비형
        save(PERSONA_2, 89000L, "쇼핑", "의류 구매", LocalDate.of(2026, 5, 3));
        save(PERSONA_2, 12000L, "카페", "카페 디저트", LocalDate.of(2026, 5, 4));
        save(PERSONA_2, 15000L, "문화생활", "영화 관람", LocalDate.of(2026, 5, 6));
        save(PERSONA_2, 25000L, "식비", "배달음식", LocalDate.of(2026, 5, 8));
        save(PERSONA_2, 65000L, "쇼핑", "화장품", LocalDate.of(2026, 5, 10));
        save(PERSONA_2, 17000L, "구독", "OTT 구독료", LocalDate.of(2026, 5, 11));
        save(PERSONA_2, 35000L, "식비", "외식", LocalDate.of(2026, 5, 14));
        save(PERSONA_2, 150000L, "여행", "주말 여행", LocalDate.of(2026, 5, 17));
        save(PERSONA_2, 45000L, "쇼핑", "전자기기 액세서리", LocalDate.of(2026, 5, 22));
        save(PERSONA_2, 28000L, "카페", "브런치 카페", LocalDate.of(2026, 5, 28));
    }

    private void save(UUID userId, Long amount, String category, String description, LocalDate spentAt) {
        expenseRepository.save(new Expense(userId, amount, category, description, spentAt));
    }
}