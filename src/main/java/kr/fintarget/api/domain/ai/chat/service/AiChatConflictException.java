package kr.fintarget.api.domain.ai.chat.service;

/**
 * 대화 턴 상한 초과, 같은 대화에 동시에 보낸 질문 (409). IllegalStateException 핸들러로 응답한다.
 */
public class AiChatConflictException extends IllegalStateException {

    public static final String TURN_LIMIT_EXCEEDED = "한 대화에서는 질문을 " + AiChatService.MAX_TURNS + "개까지 할 수 있습니다. 새 대화를 시작해주세요.";
    public static final String CONCURRENT_QUESTION = "같은 대화에 동시에 보낸 질문이 있어 처리하지 못했습니다. 다시 시도해주세요.";

    private AiChatConflictException(String message) {
        super(message);
    }

    public static AiChatConflictException turnLimitExceeded() {
        return new AiChatConflictException(TURN_LIMIT_EXCEEDED);
    }

    public static AiChatConflictException concurrentQuestion() {
        return new AiChatConflictException(CONCURRENT_QUESTION);
    }
}
