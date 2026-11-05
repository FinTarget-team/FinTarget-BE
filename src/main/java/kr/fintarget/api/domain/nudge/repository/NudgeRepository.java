package kr.fintarget.api.domain.nudge.repository;
import kr.fintarget.api.domain.nudge.entity.Nudge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface NudgeRepository extends JpaRepository<Nudge, String> {
    List<Nudge> findByUserIdOrderByCreatedAtDesc(String userId);
    long countByUserIdAndIsRead(String userId, boolean isRead);
    boolean existsByUserIdAndTypeAndCreatedAtAfter(String userId, String type, LocalDateTime time);

    @Modifying
    @Query("delete from Nudge n where n.userId = :userId")
    int deleteByUserId(String userId);
}