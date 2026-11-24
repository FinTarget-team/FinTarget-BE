package kr.fintarget.api.domain.policy.rag;

import kr.fintarget.api.domain.policy.entity.Policy;

import java.util.List;

/**
 * 통합 정책 AI 답변 서비스 결과 (#183).
 *
 * @param answer  앱에 보여줄 답변. ANSWERED가 아니면 서버 고정 문구
 * @param sources 근거 정책. 모델이 처음 인용한 순서이며 ANSWERED가 아니면 빈 목록
 */
public record PolicyRagResult(PolicyRagStatus status, String answer, List<Policy> sources) {

    // 해요체 (#235). 정책과 관계없는 질문도 검색 결과가 없어 이 문구로 끝나므로, 답할 수 있는 범위를 함께 알린다
    static final String NO_EVIDENCE_ANSWER =
            "질문에 맞는 정책을 찾지 못했어요. 청년 주거·금융·일자리 정책에 대한 질문에 답할 수 있어요. 지역이나 상황을 조금 더 구체적으로 알려주세요.";
    static final String PROFILE_INCOMPLETE_ANSWER =
            "맞춤 정책을 찾으려면 나이나 소득 정보가 필요해요. 내 정보에서 출생연도와 소득을 입력해 주세요.";

    public PolicyRagResult {
        sources = List.copyOf(sources);
    }

    static PolicyRagResult answered(String answer, List<Policy> sources) {
        return new PolicyRagResult(PolicyRagStatus.ANSWERED, answer, sources);
    }

    static PolicyRagResult noEvidence() {
        return new PolicyRagResult(PolicyRagStatus.NO_EVIDENCE, NO_EVIDENCE_ANSWER, List.of());
    }

    static PolicyRagResult profileIncomplete() {
        return new PolicyRagResult(PolicyRagStatus.PROFILE_INCOMPLETE, PROFILE_INCOMPLETE_ANSWER, List.of());
    }
}
