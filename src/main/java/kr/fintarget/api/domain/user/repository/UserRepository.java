package kr.fintarget.api.domain.user.repository;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByProviderAndProviderId(SocialProvider provider, String providerId);

    /** 온보딩을 마친 사용자 ID (추천 리랭킹 미리 계산, #229) */
    @Query("select u.id from User u where u.onboardingCompleted = true order by u.createdAt")
    List<String> findIdsByOnboardingCompletedTrue();
}
