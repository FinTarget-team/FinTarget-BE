package kr.fintarget.api.domain.ai.chat.scheduler;

import kr.fintarget.api.domain.ai.chat.service.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiConversationCleanupScheduler {

    private final AiChatService aiChatService;

    /**
     * 매일 04:00 (정책 동기화 03:00 이후), 만료된 AI 대화와 메시지 삭제
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void cleanupExpiredConversations() {
        int deleted = aiChatService.deleteExpiredConversations();
        log.info("만료된 AI 대화 정리 완료: {}건", deleted);
    }
}
