package kr.fintarget.api.domain.ai.chat.service;

/**
 * 정책 또는 대화를 찾을 수 없음 (404).
 * 다른 사용자의 대화, 다른 정책의 대화, 만료된 대화도 존재 여부를 드러내지 않도록 모두 같은 응답으로 처리한다.
 */
public class AiChatNotFoundException extends RuntimeException {

    public static final String POLICY_NOT_FOUND = "존재하지 않는 정책입니다.";
    public static final String CONVERSATION_NOT_FOUND = "대화를 찾을 수 없습니다. 새 대화를 시작해주세요.";

    private AiChatNotFoundException(String message) {
        super(message);
    }

    public static AiChatNotFoundException policy() {
        return new AiChatNotFoundException(POLICY_NOT_FOUND);
    }

    public static AiChatNotFoundException conversation() {
        return new AiChatNotFoundException(CONVERSATION_NOT_FOUND);
    }
}
