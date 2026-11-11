package kr.fintarget.api.domain.user.repository;
import kr.fintarget.api.domain.user.code.SocialProvider;
import kr.fintarget.api.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByProviderAndProviderId(SocialProvider provider, String providerId);
}
