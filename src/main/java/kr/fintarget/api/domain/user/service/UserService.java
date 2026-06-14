package kr.fintarget.api.domain.user.service;

import kr.fintarget.api.domain.user.dto.UpdateProfileRequest;
import kr.fintarget.api.domain.user.dto.UserProfileResponse;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import kr.fintarget.api.expense.repository.ExpenseRepository;
import kr.fintarget.api.goal.repository.GoalRepository;
import kr.fintarget.api.policy.repository.UserPolicyRepository;
import kr.fintarget.api.simulation.repository.SimulationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final GoalRepository goalRepository;
    private final ExpenseRepository expenseRepository;
    private final UserPolicyRepository userPolicyRepository;
    private final SimulationRepository simulationRepository;

    public UserProfileResponse getProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return UserProfileResponse.from(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        user.updateProfile(request.getName(), request.getEmail(), request.getRegion());
        return UserProfileResponse.from(user);
    }

    @Transactional
    public void withdraw(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        UUID uid = UUID.fromString(userId);

        // 외래키 제약 순서: 시뮬레이션 → 목표/정책 → 지출 → 유저
        simulationRepository.deleteByUserId(uid);
        goalRepository.deleteByUserId(uid);
        userPolicyRepository.deleteByUserId(uid);
        expenseRepository.deleteByUserId(uid);

        userRepository.delete(user);
    }
}