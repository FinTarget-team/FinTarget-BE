package kr.fintarget.api.domain.ai.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AiDailyUsageRepository extends JpaRepository<AiDailyUsage, UUID> {

    /**
     * 한도 안이면 1 올린다. 조건과 증가를 한 문장에서 해서 동시 요청이 한도를 넘지 않는다.
     *
     * @return 올렸으면 1, 행이 없거나 이미 한도면 0
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE AiDailyUsage u SET u.usedCount = u.usedCount + 1
            WHERE u.userId = :userId AND u.usageDate = :date AND u.feature = :feature AND u.usedCount < :limit
            """)
    int incrementIfBelow(@Param("userId") String userId, @Param("date") LocalDate date,
                         @Param("feature") AiUsageFeature feature, @Param("limit") int limit);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE AiDailyUsage u SET u.usedCount = u.usedCount - 1
            WHERE u.userId = :userId AND u.usageDate = :date AND u.feature = :feature AND u.usedCount > 0
            """)
    int decrement(@Param("userId") String userId, @Param("date") LocalDate date, @Param("feature") AiUsageFeature feature);

    @Modifying
    @Query("DELETE FROM AiDailyUsage u WHERE u.userId = :userId")
    int deleteByUserId(@Param("userId") String userId);

    Optional<AiDailyUsage> findByUserIdAndUsageDateAndFeature(String userId, LocalDate usageDate, AiUsageFeature feature);
}
