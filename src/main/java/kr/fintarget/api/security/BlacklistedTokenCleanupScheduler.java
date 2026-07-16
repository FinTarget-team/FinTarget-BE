package kr.fintarget.api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class BlacklistedTokenCleanupScheduler {

    private final BlacklistedTokenRepository blacklistedTokenRepository;

    /**
     * 매일 자정, 만료된 블랙리스트 토큰 삭제
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void cleanupExpiredTokens() {
        blacklistedTokenRepository.deleteAllByExpiresAtBefore(LocalDateTime.now());
    }
}
