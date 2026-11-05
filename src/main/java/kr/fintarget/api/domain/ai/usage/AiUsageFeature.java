package kr.fintarget.api.domain.ai.usage;

/**
 * 일일 한도를 따로 세는 기능 (#183). 한도 값은 각 기능의 설정에 있다 (policy.rag.daily-limit, policy.search.daily-limit).
 */
public enum AiUsageFeature {

    /** 통합 정책 AI 답변 (임베딩 + 텍스트 생성) */
    POLICY_RAG("오늘 질문할 수 있는 횟수를 모두 사용했습니다. 내일 다시 시도해주세요."),

    /** 정책 검색 API (임베딩). 통합 AI 답변 내부의 검색은 여기서 세지 않는다 */
    POLICY_SEARCH("오늘 검색할 수 있는 횟수를 모두 사용했습니다. 내일 다시 시도해주세요.");

    private final String exceededMessage;

    AiUsageFeature(String exceededMessage) {
        this.exceededMessage = exceededMessage;
    }

    public String exceededMessage() {
        return exceededMessage;
    }
}
