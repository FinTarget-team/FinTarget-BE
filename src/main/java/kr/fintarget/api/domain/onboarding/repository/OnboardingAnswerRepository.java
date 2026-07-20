package kr.fintarget.api.domain.onboarding.repository;

import kr.fintarget.api.domain.onboarding.entity.OnboardingAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OnboardingAnswerRepository extends JpaRepository<OnboardingAnswer, String> {
    List<OnboardingAnswer> findByUserIdOrderByStepAsc(UUID userId);
    void deleteByUserId(UUID userId);
    boolean existsByUserIdAndStep(UUID userId, Integer step);
}