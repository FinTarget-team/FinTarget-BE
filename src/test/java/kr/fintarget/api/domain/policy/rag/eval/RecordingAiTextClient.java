package kr.fintarget.api.domain.policy.rag.eval;

import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.ai.client.AiTextRequest;
import kr.fintarget.api.domain.ai.client.AiTextResult;

/**
 * 모델 원문 답변(근거 번호 포함)과 토큰·응답 시간을 잡아 두는 래퍼 (#184 답변 평가).
 * PolicyRagService는 근거 번호를 지운 답변만 돌려주므로, 무효 번호 수·근거 번호 누락 판정을 위해 원문을 따로 받는다.
 *
 * <p>잡아 둔 값은 평가 결과 파일(build/ai-eval/)에만 쓴다. 로그로 남기지 않는다 (운영 로그에 질문·답변 본문을 남기지 않는 원칙).
 * 문항마다 새로 만들어 쓴다 (호출 1회분만 들고 있다).
 */
final class RecordingAiTextClient implements AiTextClient {

    private final AiTextClient delegate;
    private AiTextResult result;
    private long elapsedMs;

    RecordingAiTextClient(AiTextClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public AiTextResult generate(AiTextRequest request) {
        long startedAt = System.nanoTime();
        try {
            result = delegate.generate(request);
            return result;
        } finally {
            elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        }
    }

    /** 호출하지 않았거나 실패했으면 null */
    AiTextResult result() {
        return result;
    }

    long elapsedMs() {
        return elapsedMs;
    }
}
