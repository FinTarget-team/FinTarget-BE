package kr.fintarget.api.domain.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 사용자별 SSE 연결을 메모리에 보관하고 이벤트를 보낸다.
 * 서버가 한 대라 메모리로 충분하다. 서버를 여러 대로 늘리면 Redis Pub/Sub로 다른 서버의 연결에도 전달해야 한다.
 */
@Slf4j
@Component
public class SseEmitterRegistry {

    // 한 계정이 여러 기기에서 접속할 수 있지만, 끊긴 연결이 계속 쌓이지 않도록 사용자당 개수를 제한한다
    static final int MAX_CONNECTIONS_PER_USER = 5;

    private final Map<String, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();
    private final Duration timeout;

    // 연결은 기본 30분 유지하고, 끊기면 앱이 다시 연결한다
    public SseEmitterRegistry(@Value("${realtime.sse-timeout:30m}") Duration timeout) {
        this.timeout = timeout;
    }

    public SseEmitter connect(String userId) {
        SseEmitter emitter = new SseEmitter(timeout.toMillis());
        List<SseEmitter> emitters = emittersByUser.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        // 시간 초과는 정상 종료로 처리한다. complete하지 않으면 AsyncRequestTimeoutException이 전역 예외 처리기에서 ERROR로 남는다
        emitter.onTimeout(() -> {
            remove(userId, emitter);
            emitter.complete();
        });
        emitter.onError(e -> remove(userId, emitter));

        while (emitters.size() > MAX_CONNECTIONS_PER_USER) {
            SseEmitter oldest = emitters.removeFirst();
            oldest.complete();
        }

        send(userId, emitter, SseEmitter.event().name("connected").data(Map.of("userId", userId), MediaType.APPLICATION_JSON));
        return emitter;
    }

    /**
     * 그 사용자의 모든 연결로 이벤트를 보낸다. 실패한 연결은 정리할 뿐 예외를 던지지 않는다.
     */
    public void sendToUser(String userId, String eventName, Object data) {
        for (SseEmitter emitter : emittersByUser.getOrDefault(userId, List.of())) {
            send(userId, emitter, SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
        }
    }

    // 중간 네트워크 장비가 오래 조용한 연결을 끊지 않도록 주기적으로 주석 줄을 보내고, 이미 끊긴 연결을 정리한다
    @Scheduled(fixedRate = 30_000)
    public void sendHeartbeat() {
        emittersByUser.forEach((userId, emitters) ->
                emitters.forEach(emitter -> send(userId, emitter, SseEmitter.event().comment("heartbeat"))));
    }

    int connectionCount(String userId) {
        return emittersByUser.getOrDefault(userId, List.of()).size();
    }

    private void send(String userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // 앱이 꺼졌거나 네트워크가 끊긴 경우. 정상적인 상황이라 debug로만 남긴다
            log.debug("SSE 전송 실패로 연결을 정리합니다. userId={}", userId, e);
            remove(userId, emitter);
            emitter.completeWithError(e);
        }
    }

    private void remove(String userId, SseEmitter emitter) {
        emittersByUser.computeIfPresent(userId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
