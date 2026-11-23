package kr.fintarget.api.domain.policy.condition;

import kr.fintarget.api.domain.ai.client.AiCallFailedException;
import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiMessage;
import kr.fintarget.api.domain.ai.client.AiModelTier;
import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.ai.client.AiTextRequest;
import kr.fintarget.api.domain.ai.client.AiTextResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 정책 원문에서 신청 조건(소득·주택·대상·지역)을 추출한다 (#220).
 * AI는 동기화·백필 때만 호출하고, 추천 매칭은 저장된 결과(PolicyCondition)만 읽는다.
 *
 * <p>모델은 설정의 기본 등급(openai.model.default)을 쓴다. 현재 클라이언트는 temperature·JSON 모드 옵션이 없어,
 * 혜택 추출(#162)과 같이 프롬프트로 JSON 하나만 출력하도록 지시하고 {@link ConditionExtractionParser}로 검증한다.
 *
 * <p>일시적인 호출 실패(시간 초과, 연결 실패, 서버 오류, 불완전 응답)는 한 번만 다시 시도한다.
 * 클라이언트 자체 재시도(openai.max-retries, 429·5xx·연결 실패)와 별개다.
 * 그래도 실패하면 AiClientException을 그대로 던지고, 응답 해석 실패는 ConditionExtractionException으로 던진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyConditionExtractor {

    // 출력은 JSON 한 줄(약 60토큰)이고 추론 토큰을 더해도 이 안에 든다
    static final int MAX_OUTPUT_TOKENS = 1_000;
    private static final Set<AiClientException.Reason> RETRYABLE = EnumSet.of(
            AiClientException.Reason.TIMEOUT, AiClientException.Reason.CONNECTION_FAILED,
            AiClientException.Reason.SERVER_ERROR, AiClientException.Reason.INCOMPLETE,
            AiClientException.Reason.INVALID_RESPONSE);

    private final AiTextClient aiTextClient;
    private final PolicyConditionPrompt prompt;

    public ConditionExtraction extract(ConditionSource source) {
        if (!source.hasContent()) {
            return ConditionExtraction.unknown();
        }
        AiTextRequest request = new AiTextRequest(prompt.instructions(),
                List.of(AiMessage.user(prompt.userMessage(source))), AiModelTier.DEFAULT, MAX_OUTPUT_TOKENS);
        return ConditionExtractionParser.parse(generateWithRetry(request, source.name()).text());
    }

    public String promptVersion() {
        return prompt.version();
    }

    private AiTextResult generateWithRetry(AiTextRequest request, String policyName) {
        try {
            return aiTextClient.generate(request);
        } catch (AiCallFailedException e) {
            if (!RETRYABLE.contains(e.getReason())) {
                throw e;
            }
            log.info("정책 조건 추출 재시도 policy={} reason={}", policyName, e.getReason());
            return aiTextClient.generate(request);
        }
    }
}
