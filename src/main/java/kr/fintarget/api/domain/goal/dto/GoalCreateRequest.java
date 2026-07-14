package kr.fintarget.api.domain.goal.dto;

import java.time.LocalDate;

public record GoalCreateRequest(
    String title,
    Long targetAmount,
    Long currentAmount,
    LocalDate deadline
) {}
