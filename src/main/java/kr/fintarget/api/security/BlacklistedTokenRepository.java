package kr.fintarget.api.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, String> {
    List<BlacklistedToken> findByExpiresAtBefore(LocalDateTime dateTime);
}