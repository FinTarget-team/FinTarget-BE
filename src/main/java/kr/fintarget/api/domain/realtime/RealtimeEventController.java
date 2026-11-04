package kr.fintarget.api.domain.realtime;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "실시간 이벤트", description = "앱이 켜져 있는 동안 서버의 변경을 실시간으로 받는 SSE API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class RealtimeEventController {

    private final SseEmitterRegistry sseEmitterRegistry;

    @Operation(
            summary = "실시간 이벤트 구독 (SSE)",
            description = "Authorization 헤더에 액세스 토큰을 넣어 연결하면 text/event-stream으로 이벤트를 계속 받습니다. "
                    + "연결 직후 connected 이벤트가 한 번 오고, 30초마다 heartbeat 주석(`: heartbeat`)이 옵니다. "
                    + "은행 거래(mock-bank)로 지출이 새로 저장되면 expense-created 이벤트가 오며, data.expenses는 지출 목록 조회와 같은 형식입니다. "
                    + "이벤트를 받으면 홈·지출 목록·넛지를 다시 조회하세요. 넛지는 넛지 목록을 조회할 때 만들어집니다. "
                    + "연결은 최대 30분 유지되고 서버 재배포 때도 끊기므로, 끊기면 몇 초 뒤 다시 연결하세요. "
                    + "같은 계정은 최대 5개까지 동시에 연결되며, 넘으면 가장 오래된 연결이 닫힙니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "연결 성공. 이벤트 스트림",
                    content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE, examples = @ExampleObject(
                            name = "이벤트 예시",
                            value = """
                                    event:connected
                                    data:{"userId":"3fa85f64-5717-4562-b3fc-2c963f66afa6"}

                                    :heartbeat

                                    event:expense-created
                                    data:{"expenses":[{"expenseId":"7c9e6679-7425-40de-944b-e07fc1f90ae7","amount":15000,"category":"식비","description":"스타벅스","spentAt":"2026-11-04","createdAt":"2026-11-04T14:30:00"}]}
                                    """
                    ))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "토큰 없음 또는 유효하지 않은 토큰",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
    })
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal String userId) {
        return sseEmitterRegistry.connect(userId);
    }
}
