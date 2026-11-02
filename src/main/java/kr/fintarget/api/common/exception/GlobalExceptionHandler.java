package kr.fintarget.api.common.exception;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.ai.chat.service.AiChatNotFoundException;
import kr.fintarget.api.domain.ai.client.AiCallFailedException;
import kr.fintarget.api.domain.ai.client.AiUnavailableException;
import kr.fintarget.api.domain.auth.KakaoOAuthException;
import kr.fintarget.api.domain.auth.NaverOAuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final String INTERNAL_ERROR_MESSAGE = "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
    }
    @ExceptionHandler(AiChatNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleAiChatNotFound(AiChatNotFoundException e) {
        return ResponseEntity.status(404).body(ApiResponse.error(404, e.getMessage()));
    }
    @ExceptionHandler(NaverOAuthException.class)
    public ResponseEntity<ApiResponse<?>> handleNaverOAuth(NaverOAuthException e) {
        return ResponseEntity.status(401).body(ApiResponse.error(401, e.getMessage()));
    }
    @ExceptionHandler(KakaoOAuthException.class)
    public ResponseEntity<ApiResponse<?>> handleKakaoOAuth(KakaoOAuthException e) {
        return ResponseEntity.status(401).body(ApiResponse.error(401, e.getMessage()));
    }
    // 메시지는 사용자에게 보여도 되는 고정 문구 (OpenAI 응답 내용을 담지 않음)
    @ExceptionHandler(AiUnavailableException.class)
    public ResponseEntity<ApiResponse<?>> handleAiUnavailable(AiUnavailableException e) {
        return ResponseEntity.status(503).body(ApiResponse.error(503, e.getMessage()));
    }
    @ExceptionHandler(AiCallFailedException.class)
    public ResponseEntity<ApiResponse<?>> handleAiCallFailed(AiCallFailedException e) {
        return ResponseEntity.status(502).body(ApiResponse.error(502, e.getMessage()));
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(409).body(ApiResponse.error(409, e.getMessage()));
    }
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<?>> handleSecurity(SecurityException e) {
        return ResponseEntity.status(401).body(ApiResponse.error(401, e.getMessage()));
    }
    // ===== 클라이언트 요청 형식 오류: 아래 매핑이 없으면 Exception 처리로 떨어져 500이 된다 =====
    // JSON 문법 오류, UUID·날짜 형식 오류, 본문 누락
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "요청 본문의 형식이 올바르지 않습니다. (JSON 문법, 날짜는 yyyy-MM-dd, ID는 UUID 형식)"));
    }
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingParameter(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "필수 파라미터가 없습니다: " + e.getParameterName()));
    }
    // 쿼리·경로 파라미터 형식 오류 (예: start=2026/11/01, userPolicyId=abc)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "파라미터 형식이 올바르지 않습니다: " + e.getName()));
    }
    // List<@Valid ...> 같은 메서드 파라미터 검증 실패
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodValidation(HandlerMethodValidationException e) {
        String message = e.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse("잘못된 요청입니다.");
        return ResponseEntity.badRequest().body(ApiResponse.error(400, message));
    }
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(404).body(ApiResponse.error(404, "요청한 API를 찾을 수 없습니다."));
    }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(405).body(ApiResponse.error(405, "지원하지 않는 HTTP 메서드입니다: " + e.getMethod()));
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(415).body(ApiResponse.error(415, "Content-Type은 application/json이어야 합니다."));
    }

    // ===== 예상하지 못한 오류 =====
    // 내부 예외 메시지(SQL, 클래스 이름 등)가 응답으로 나가지 않도록 고정 문구만 내려주고, 원인은 서버 로그에 남긴다
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<?>> handleRuntime(RuntimeException e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(500).body(ApiResponse.error(500, INTERNAL_ERROR_MESSAGE));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(500).body(ApiResponse.error(500, INTERNAL_ERROR_MESSAGE));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("잘못된 요청입니다.");
        return ResponseEntity.badRequest().body(ApiResponse.error(400, message));
    }
}