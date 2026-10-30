package kr.fintarget.api.domain.ai.chat.eval;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.fintarget.api.domain.ai.chat.service.PolicyDocument;
import kr.fintarget.api.domain.ai.chat.service.UserProfileSummary;
import kr.fintarget.api.domain.ai.client.AiMessage;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 정책 AI 질문 답변 품질 평가셋 (#126). src/test/resources/ai-eval/policy-qa-cases.json을 읽는다.
 *
 * <p>정책 원문은 DB가 아니라 파일에 복사해 둔 스냅샷을 쓴다. 정책 ID(UUID)는 DB마다 다르고 원문은 매일 재동기화로 바뀔 수 있어,
 * 프롬프트·모델을 바꿔도 같은 입력으로 비교하기 위함이다.
 */
record PolicyQaEvalSet(String description, Map<String, PolicySnapshot> policies,
                       Map<String, UserProfileSummary> profiles, List<EvalCase> cases) {

    static final String RESOURCE = "ai-eval/policy-qa-cases.json";

    static PolicyQaEvalSet load() {
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return objectMapper.readValue(in, PolicyQaEvalSet.class);
        } catch (IOException e) {
            throw new UncheckedIOException("평가셋을 읽을 수 없습니다: " + RESOURCE, e);
        }
    }

    PolicyDocument documentOf(EvalCase evalCase) {
        return PolicyDocument.of(policies.get(evalCase.policy()).toPolicy());
    }

    UserProfileSummary profileOf(EvalCase evalCase) {
        return profiles.get(evalCase.profile());
    }

    /** 질문 유형 */
    enum Category {
        ELIGIBILITY, PROFILE_CHECK, NOT_IN_SOURCE, SUPPORT, APPLY_METHOD, DOCUMENTS, FOLLOW_UP, OFF_TOPIC, INJECTION
    }

    /** 기대하는 답변 행동 */
    enum Behavior {
        // 원문 근거로 답한다
        ANSWER,
        // 원문에서 확인할 수 없다고 안내한다
        NOT_IN_SOURCE,
        // 사용자 정보로 판단할 수 없어 확인이 필요하다고 안내한다
        NEEDS_CHECK,
        // 이 정책에 대한 질문만 답할 수 있다고 안내한다
        OFF_TOPIC,
        // 규칙을 바꾸거나 무시하라는 요청을 따르지 않는다
        REFUSE
    }

    /**
     * @param expectedFacts          답변에 있어야 하는 원문 사실. 그룹마다 대안 중 하나가 있으면 된다 (원문에도 있어야 함)
     * @param mustInclude            답변에 있어야 하는 안내 (문의처 등, 원문 외 표현 가능)
     * @param mustNotInclude         답변에 있으면 안 되는 표현 (지어낸 값, 프롬프트 유출 등)
     * @param noDefinitiveEligibility 자격을 단정하는 표현이 있으면 실패로 본다
     */
    record EvalCase(String id, String policy, String profile, Category category, Behavior behavior, String question,
                    List<Turn> history, List<List<String>> expectedFacts, List<List<String>> mustInclude,
                    List<String> mustNotInclude, boolean noDefinitiveEligibility, String note) {

        EvalCase {
            history = history == null ? List.of() : List.copyOf(history);
            expectedFacts = expectedFacts == null ? List.of() : List.copyOf(expectedFacts);
            mustInclude = mustInclude == null ? List.of() : List.copyOf(mustInclude);
            mustNotInclude = mustNotInclude == null ? List.of() : List.copyOf(mustNotInclude);
        }

        List<AiMessage> messages() {
            List<AiMessage> messages = new ArrayList<>(history.stream().map(Turn::toMessage).toList());
            messages.add(AiMessage.user(question));
            return messages;
        }
    }

    record Turn(String role, String content) {

        AiMessage toMessage() {
            return "assistant".equals(role) ? AiMessage.assistant(content) : AiMessage.user(content);
        }
    }

    /** 2026-10-30 로컬 DB 동기화 결과에서 복사한 정책 1건 */
    record PolicySnapshot(String source, String policyType, String name, String externalId, String region,
                          Integer minAge, Integer maxAge, Long incomeLimit, Long benefitAmount, String description,
                          String supportContent, String eligibilityDetail, String exclusionCriteria,
                          String incomeCondition, String ageCondition, String applyMethod, String screeningMethod,
                          String requiredDocuments, String applyPeriod, String businessPeriod, String referenceUrl,
                          String applyUrl, String contactInfo, String etcNote) {

        Policy toPolicy() {
            PolicyText text = PolicyText.builder()
                    .supportContent(supportContent)
                    .eligibilityDetail(eligibilityDetail)
                    .exclusionCriteria(exclusionCriteria)
                    .incomeCondition(incomeCondition)
                    .ageCondition(ageCondition)
                    .applyMethod(applyMethod)
                    .screeningMethod(screeningMethod)
                    .requiredDocuments(requiredDocuments)
                    .applyPeriod(applyPeriod)
                    .businessPeriod(businessPeriod)
                    .referenceUrl(referenceUrl)
                    .applyUrl(applyUrl)
                    .contactInfo(contactInfo)
                    .etcNote(etcNote)
                    .build();
            return Policy.create(name, description, minAge, maxAge, incomeLimit, benefitAmount,
                    PolicyType.valueOf(policyType), region, externalId, source, text);
        }
    }
}
