package kr.fintarget.api.domain.policy.search;

/**
 * 검색 문장 바꿔 쓰기 결과 (#241).
 *
 * @param text      바꾼 검색 문장. {@link Outcome#REWRITTEN}일 때만 값이 있고 나머지는 null
 * @param outcome   결과
 * @param elapsedMs 바꿔 쓰기에 든 시간 (시간 초과면 기다린 시간)
 */
public record QueryRewrite(String text, Outcome outcome, long elapsedMs) {

    public enum Outcome {
        /** 검색 문장을 받았다 */
        REWRITTEN,
        /** 정책과 관계없는 질문이라 빈 문자열을 받았다 */
        EMPTY,
        /** 호출 실패 (OpenAI 오류, 출력 상한 초과, 키 없음 등) */
        FAILED,
        /** 시간 초과 */
        TIMEOUT
    }

    public QueryRewrite {
        if (outcome == Outcome.REWRITTEN && (text == null || text.isBlank())) {
            throw new IllegalArgumentException("바꾼 문장이 비어 있습니다.");
        }
        text = outcome == Outcome.REWRITTEN ? text.strip() : null;
    }

    public static QueryRewrite rewritten(String text, long elapsedMs) {
        return new QueryRewrite(text, Outcome.REWRITTEN, elapsedMs);
    }

    public static QueryRewrite of(Outcome outcome, long elapsedMs) {
        return new QueryRewrite(null, outcome, elapsedMs);
    }

    public boolean hasText() {
        return outcome == Outcome.REWRITTEN;
    }
}
