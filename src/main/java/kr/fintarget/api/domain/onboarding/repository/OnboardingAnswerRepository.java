package kr.fintarget.api.domain.onboarding.repository;

import kr.fintarget.api.domain.onboarding.entity.OnboardingAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnboardingAnswerRepository extends JpaRepository<OnboardingAnswer, String> {
    List<OnboardingAnswer> findByUserIdOrderByStepAsc(UUID userId);
    void deleteByUserId(UUID userId);
    // 유니크 제약이 생기기 전에 쌓인 중복이 남아 있어도 오류 없이 최신 답변을 고르도록 정렬해서 하나만 가져온다
    Optional<OnboardingAnswer> findFirstByUserIdAndStepOrderByCreatedAtDesc(UUID userId, Integer step);
    boolean existsByUserIdAndStep(UUID userId, Integer step);
}