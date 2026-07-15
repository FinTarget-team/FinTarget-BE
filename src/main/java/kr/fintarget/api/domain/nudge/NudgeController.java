package kr.fintarget.api.domain.nudge;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.nudge.service.NudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/nudges")
@RequiredArgsConstructor
public class NudgeController {
    private final NudgeService nudgeService;
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getNudges(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(nudgeService.getNudges(userId)));
    }
    @PatchMapping("/{nudgeId}/read")
    public ResponseEntity<ApiResponse<?>> markAsRead(
            @AuthenticationPrincipal String userId,
            @PathVariable String nudgeId) {
        nudgeService.markAsRead(userId, nudgeId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
    @DeleteMapping("/{nudgeId}")
    public ResponseEntity<ApiResponse<?>> deleteNudge(
            @AuthenticationPrincipal String userId,
            @PathVariable String nudgeId) {
        nudgeService.deleteNudge(userId, nudgeId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
