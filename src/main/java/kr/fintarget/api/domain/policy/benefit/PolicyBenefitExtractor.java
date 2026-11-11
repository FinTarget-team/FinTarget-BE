package kr.fintarget.api.domain.policy.benefit;

import kr.fintarget.api.domain.ai.client.AiMessage;
import kr.fintarget.api.domain.ai.client.AiModelTier;
import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.ai.client.AiTextRequest;
import kr.fintarget.api.domain.ai.client.AiTextResult;
import kr.fintarget.api.domain.policy.mapper.KinfaPolicyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 정책 원문에서 시뮬레이션에 쓸 현금 혜택을 추출한다 (#162).
 * AI는 동기화·백필 때만 호출하고, 시뮬레이션은 저장된 결과(PolicyBenefit)만 읽는다.
 *
 * <p>AI 호출 실패는 AiClientException, 응답 해석 실패는 BenefitExtractionException으로 던진다.
 * 호출 쪽은 해당 정책만 건너뛰고 기존 값을 유지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyBenefitExtractor {

    private final AiTextClient aiTextClient;
    private final PolicyBenefitPrompt prompt;

    public BenefitExtraction extract(BenefitSource source) {
        // KINFA는 전부 대출 상품이라 AI 없이 LOAN으로 분류한다 (#151, 2026-10 기준 329건 전부 대출)
        if (KinfaPolicyMapper.SOURCE.equals(source.source())) {
            return BenefitExtraction.of(BenefitType.LOAN, null, null);
        }
        if (!source.hasContent()) {
            return BenefitExtraction.of(BenefitType.UNKNOWN, null, "지원 내용과 설명이 비어 있음");
        }
        AiTextResult result = aiTextClient.generate(new AiTextRequest(
                prompt.instructions(), List.of(AiMessage.user(prompt.userMessage(source))), AiModelTier.DEFAULT));
        BenefitExtraction extraction = BenefitExtractionParser.parse(result.text(), source);
        if (extraction.validationNote() != null) {
            log.info("정책 혜택 추출 결과 보정 policy={} type={} note={}", source.name(), extraction.type(),
                    extraction.validationNote());
        }
        return extraction;
    }

    public String promptVersion() {
        return prompt.version();
    }
}
