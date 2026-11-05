package kr.fintarget.api.domain.policy.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.fintarget.api.domain.policy.search.PolicySearchHit;

import java.util.List;

public record PolicySearchResponse(
        @ArraySchema(arraySchema = @Schema(
                description = "질문과 가까운 정책 목록. 유사도가 가까운 순서이며, 맞는 정책이 없으면 빈 배열입니다."))
        List<Item> results
) {
    public static PolicySearchResponse from(List<PolicySearchHit> hits) {
        return new PolicySearchResponse(hits.stream().map(Item::from).toList());
    }

    public record Item(
            @Schema(description = "정책 정보. 정책 목록 API와 같은 형식입니다.")
            PolicyResponse policy,
            @Schema(description = "질문과의 유사도 (0.0~1.0, 1에 가까울수록 유사). 절대 기준이 아니라 결과 간 비교용입니다.",
                    example = "0.8634")
            double score,
            @Schema(description = "질문과 가장 가까웠던 원문 섹션. 어느 부분이 걸렸는지 확인용입니다.",
                    example = "정책 설명,지원 내용")
            String sectionLabel
    ) {
        // 청크 본문(content)은 넣지 않는다. 길고 앱이 쓰지 않으며, 통합 AI 답변(#183)만 서비스 계층에서 받아 쓴다
        static Item from(PolicySearchHit hit) {
            return new Item(PolicyResponse.from(hit.policy()), hit.score(), hit.sectionLabel());
        }
    }
}
