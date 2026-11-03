package kr.fintarget.api.domain.policy.benefit.eval;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.fintarget.api.domain.policy.benefit.BenefitSource;
import kr.fintarget.api.domain.policy.benefit.BenefitType;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * 정책 혜택 추출 정확도 평가셋 (#162). src/test/resources/ai-eval/policy-benefit-cases.json을 읽는다.
 * 정책 원문은 온통청년 API 응답을 복사한 스냅샷이라 재동기화와 무관하게 같은 입력으로 프롬프트·모델을 비교할 수 있다.
 */
record PolicyBenefitEvalSet(String description, List<String> rules, List<EvalCase> cases) {

    static final String RESOURCE = "ai-eval/policy-benefit-cases.json";

    static PolicyBenefitEvalSet load() {
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return objectMapper.readValue(in, PolicyBenefitEvalSet.class);
        } catch (IOException e) {
            throw new UncheckedIOException("평가셋을 읽을 수 없습니다: " + RESOURCE, e);
        }
    }

    record EvalCase(String id, PolicySnapshot policy, Expected expected, String reviewNote) {
    }

    record PolicySnapshot(String source, String name, String category, String description, String supportContent,
                          String businessPeriod, String etcNote) {

        BenefitSource toSource() {
            return new BenefitSource(source, name, supportContent, description, businessPeriod, etcNote);
        }
    }

    record Expected(BenefitType type, Long monthlyAmount, Integer months, Long oneTimeAmount, boolean isMaximum,
                    String evidence) {
    }
}
