package kr.fintarget.api.domain.ai.usage;

/**
 * 사용자별 일일 AI 호출 한도 초과 (#183). 429로 응답한다. 메시지는 사용자에게 보여도 되는 고정 문구다.
 */
public class AiUsageLimitExceededException extends RuntimeException {

    private final AiUsageFeature feature;

    public AiUsageLimitExceededException(AiUsageFeature feature) {
        super(feature.exceededMessage());
        this.feature = feature;
    }

    public AiUsageFeature getFeature() {
        return feature;
    }
}
