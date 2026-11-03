package kr.fintarget.api.domain.policy.benefit;

import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiModelTier;
import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.ai.client.AiTextRequest;
import kr.fintarget.api.domain.ai.client.AiTextResult;
import kr.fintarget.api.domain.ai.client.AiUnavailableException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PolicyBenefitExtractorTest {

    private final AiTextClient aiTextClient = mock(AiTextClient.class);
    private final PolicyBenefitExtractor extractor = new PolicyBenefitExtractor(aiTextClient, new PolicyBenefitPrompt());

    @Test
    void KINFA_정책은_AI를_호출하지_않고_LOAN으로_분류한다() {
        BenefitExtraction result = extractor.extract(
                new BenefitSource("KINFA", "새희망홀씨", "대출 한도: 3500만원", "근로자", null, null));

        assertThat(result.type()).isEqualTo(BenefitType.LOAN);
        verifyNoInteractions(aiTextClient);
    }

    @Test
    void 지원_내용과_설명이_없으면_AI를_호출하지_않고_UNKNOWN이다() {
        BenefitExtraction result = extractor.extract(new BenefitSource("YOUTH_CENTER", "정책", " ", null, null, null));

        assertThat(result.type()).isEqualTo(BenefitType.UNKNOWN);
        verifyNoInteractions(aiTextClient);
    }

    @Test
    void 원문을_사용자_메시지로_보내고_응답을_검증해_돌려준다() {
        when(aiTextClient.generate(any())).thenReturn(new AiTextResult("""
                {"type": "CASH", "monthlyAmount": 500000, "months": 60, "evidence": "월 50만원 지급 (최대 5년)"}""",
                "model", 10, 10, 0));
        BenefitSource source = new BenefitSource("YOUTH_CENTER", "자립수당", "월 50만원 지급 (최대 5년)", null, null, null);

        BenefitExtraction result = extractor.extract(source);

        assertThat(result.type()).isEqualTo(BenefitType.CASH);
        assertThat(result.months()).isEqualTo(60);
        ArgumentCaptor<AiTextRequest> captor = ArgumentCaptor.forClass(AiTextRequest.class);
        verify(aiTextClient).generate(captor.capture());
        AiTextRequest request = captor.getValue();
        assertThat(request.tier()).isEqualTo(AiModelTier.DEFAULT);
        assertThat(request.systemPrompt()).doesNotContain("월 50만원 지급 (최대 5년)");
        assertThat(request.messages().getFirst().content()).contains("월 50만원 지급 (최대 5년)");
    }

    @Test
    void AI_호출_실패는_그대로_던진다() {
        when(aiTextClient.generate(any())).thenThrow(new AiUnavailableException(AiClientException.Reason.OVERLOADED));

        assertThatThrownBy(() -> extractor.extract(
                new BenefitSource("YOUTH_CENTER", "정책", "월 10만원", null, null, null)))
                .isInstanceOf(AiUnavailableException.class);
    }
}
