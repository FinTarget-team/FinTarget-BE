package kr.fintarget.api.domain.user.service;

import kr.fintarget.api.domain.user.code.EmploymentType;
import kr.fintarget.api.domain.user.code.HousingStatus;
import kr.fintarget.api.domain.user.code.IncomeBracket;
import kr.fintarget.api.domain.user.code.Region;
import kr.fintarget.api.domain.user.dto.UpdateProfileRequest;
import kr.fintarget.api.domain.user.dto.UserProfileResponse;
import kr.fintarget.api.domain.user.entity.User;
import kr.fintarget.api.domain.user.repository.UserRepository;
import kr.fintarget.api.domain.user.validation.BirthYearValidator;
import kr.fintarget.api.domain.user.validation.NameValidator;
import kr.fintarget.api.domain.expense.repository.ExpenseRepository;
import kr.fintarget.api.domain.goal.repository.GoalRepository;
import kr.fintarget.api.domain.policy.repository.UserPolicyRepository;
import kr.fintarget.api.domain.simulation.repository.SimulationRepository;
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

        String validatedName = request.getName() != null ? NameValidator.validate(request.getName()) : null;
        String validatedRegion = request.getRegion() != null ? resolveRegion(request.getRegion()) : null;
        user.updateProfile(validatedName, request.getEmail(), validatedRegion);

        if (request.getBirthYear() != null) {
            user.updateBirthYear(BirthYearValidator.validate(request.getBirthYear()));
        }
        if (request.getEmploymentType() != null) {
            user.updateEmploymentType(resolveEmploymentType(request.getEmploymentType()));
        }
        if (request.getIncomeBracket() != null) {
            user.updateIncome(resolveIncome(request.getIncomeBracket()));
        }
        // null은 기존 값 유지. 선택 안 함으로 되돌리려면 NOT_SPECIFIED를 보낸다 (null로 저장됨)
        if (request.getHousingStatus() != null) {
            user.updateHousingStatus(resolveHousingStatus(request.getHousingStatus()));
        }

        return UserProfileResponse.from(user);
    }

    private String resolveHousingStatus(String housingStatusCode) {
        HousingStatus housingStatus = HousingStatus.fromCode(housingStatusCode);
        if (housingStatus == null) {
            throw new IllegalArgumentException("유효하지 않은 주택 보유 코드입니다");
        }
        return housingStatus.storedValue();
    }

    private Long resolveIncome(String incomeBracketCode) {
        IncomeBracket incomeBracket = IncomeBracket.fromCode(incomeBracketCode);
        if (incomeBracket == null) {
            throw new IllegalArgumentException("유효하지 않은 소득 코드입니다");
        }
        return incomeBracket.getIncome();
    }

    private String resolveEmploymentType(String employmentTypeCode) {
        EmploymentType employmentType = EmploymentType.fromCode(employmentTypeCode);
        if (employmentType == null) {
            throw new IllegalArgumentException("유효하지 않은 고용 형태 코드입니다");
        }
        return employmentType.getCode();
    }

    private String resolveRegion(String regionCode) {
        Region region = Region.fromCode(regionCode);
        if (region == null) {
            throw new IllegalArgumentException("유효하지 않은 지역 코드입니다");
        }
        return region.getCode();
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