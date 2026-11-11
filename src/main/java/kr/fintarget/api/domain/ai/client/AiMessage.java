package kr.fintarget.api.domain.ai.client;

import java.util.Objects;

public record AiMessage(Role role, String content) {

    public enum Role {
        USER,
        ASSISTANT
    }

    public AiMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
    }

    public static AiMessage user(String content) {
        return new AiMessage(Role.USER, content);
    }

    public static AiMessage assistant(String content) {
        return new AiMessage(Role.ASSISTANT, content);
    }
}
