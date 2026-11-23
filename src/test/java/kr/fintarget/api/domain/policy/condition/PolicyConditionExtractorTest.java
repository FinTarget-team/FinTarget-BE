package kr.fintarget.api.domain.policy.condition;

import kr.fintarget.api.domain.ai.client.AiCallFailedException;
import kr.fintarget.api.domain.ai.client.AiClientException;
import kr.fintarget.api.domain.ai.client.AiTextClient;
import kr.fintarget.api.domain.ai.client.AiTextRequest;
import kr.fintarget.api.domain.ai.client.AiTextResult;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyConditionExtractorTest {

    private static final String JSON =
            "{\"incomeBasis\": \"NONE\", \"housingRequirement\": \"HOMELESS_REQUIRED\", \"targetType\": \"INDIVIDUAL\"}";

    private final AiTextClient aiTextClient = mock(AiTextClient.class);
    private final PolicyConditionExtractor extractor = new PolicyConditionExtractor(aiTextClient, new PolicyConditionPrompt());

    private static ConditionSource source() {
        return ConditionSource.of(Policy.create("청년 월세", "무주택 청년 월세 지원", 19, 34, null, null, PolicyType.HOUSING,
                null, "P-1", "YOUTH_CENTER", PolicyText.builder().eligibilityDetail("무주택자").build()));
    }

    private static AiTextResult result(String text) {
        return new AiTextResult(text, "gpt-6-luna", 1_300, 60, 0);
    }

    @Test
    void 원문을_사용자_메시지로_보내고_응답을_해석한다() {
        when(aiTextClient.generate(any())).thenReturn(result(JSON));

        ConditionExtraction extraction = extractor.extract(source());

        ArgumentCaptor<AiTextRequest> request = ArgumentCaptor.forClass(AiTextRequest.class);
        verify(aiTextClient).generate(request.capture());
        assertThat(request.getValue().systemPrompt()).contains("incomeBasis", "HOMELESS_REQUIRED");
        assertThat(request.getValue().messages().get(0).content()).startsWith("[정책 원문]\n[정책명]\n청년 월세");
        assertThat(request.getValue().maxOutputTokens()).isEqualTo(PolicyConditionExtractor.MAX_OUTPUT_TOKENS);
        assertThat(extraction.housingRequirement()).isEqualTo(HousingRequirement.HOMELESS_REQUIRED);
        assertThat(extractor.promptVersion()).isEqualTo(PolicyConditionPrompt.VERSION);
    }

    @Test
    void 일시적인_호출_실패는_한_번만_다시_시도한다() {
        when(aiTextClient.generate(any()))
                .thenThrow(AiClientException.of(AiClientException.Reason.TIMEOUT))
                .thenReturn(result(JSON));

        assertThat(extractor.extract(source()).targetType()).isEqualTo(TargetType.INDIVIDUAL);
        verify(aiTextClient, times(2)).generate(any());
    }

    @Test
    void 다시_시도해도_실패하면_예외를_던진다() {
        when(aiTextClient.generate(any())).thenThrow(AiClientException.of(AiClientException.Reason.SERVER_ERROR));

        assertThatThrownBy(() -> extractor.extract(source())).isInstanceOf(AiCallFailedException.class);
        verify(aiTextClient, times(2)).generate(any());
    }

    @Test
    void 요청_거절처럼_다시_해도_같은_실패는_재시도하지_않는다() {
        when(aiTextClient.generate(any())).thenThrow(AiClientException.of(AiClientException.Reason.REQUEST_REJECTED));

        assertThatThrownBy(() -> extractor.extract(source())).isInstanceOf(AiCallFailedException.class);
        verify(aiTextClient, times(1)).generate(any());
    }

    @Test
    void 판단할_원문이_없으면_AI를_호출하지_않고_UNKNOWN이다() {
        ConditionSource empty = ConditionSource.of(Policy.create("이름만", null, null, null, null, null, PolicyType.ETC,
                null, "P-2", "YOUTH_CENTER", null));

        assertThat(extractor.extract(empty)).isEqualTo(ConditionExtraction.unknown());
        verify(aiTextClient, never()).generate(any());
    }
}
