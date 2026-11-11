package kr.fintarget.api.domain.policy.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.rag.PolicyRagResult;
import kr.fintarget.api.domain.policy.rag.PolicyRagStatus;

import java.util.List;
import java.util.UUID;

public record PolicyRagResponse(
        @Schema(description = "ANSWERED: 정책 원문을 근거로 답함 / NO_EVIDENCE: 질문과 관련된 정책을 찾지 못함 / "
                + "PROFILE_INCOMPLETE: 나이 또는 소득 정보가 없어 맞춤 정책을 고를 수 없음",
                example = "ANSWERED")
        PolicyRagStatus status,

        @Schema(description = "앱에 보여줄 답변 (일반 텍스트, 마크다운·URL 없음). ANSWERED가 아니면 서버 안내 문구입니다.",
                example = "원문 기준으로 신청을 검토해 볼 수 있는 월세 지원 정책은 2개입니다.\n- 청년 월세 한시 특별지원은 월 최대 20만원을 최장 12개월 지원합니다\n- 서울시 청년월세지원은 서울 거주 무주택 청년이면 신청할 수 있습니다")
        String answer,

        @ArraySchema(arraySchema = @Schema(description = "답변의 근거 정책. 답변에서 처음 인용한 순서이며, ANSWERED가 아니면 빈 배열입니다. "
                + "후속 질문은 정책 ID로 단건 정책 AI 질문(POST /api/policies/{policyId}/ai-chat)에 이어갑니다."))
        List<Source> sources
) {
    public static PolicyRagResponse from(PolicyRagResult result) {
        return new PolicyRagResponse(result.status(), result.answer(), result.sources().stream().map(Source::from).toList());
    }

    public record Source(
            @Schema(description = "정책 ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            UUID policyId,

            @Schema(description = "정책명. 이름이 같은 정책이 지역별로 여러 건일 수 있어 regions와 함께 표시합니다.",
                    example = "전세보증금반환보증 보증료 지원")
            String name,

            @ArraySchema(
                    arraySchema = @Schema(description = "신청 가능 지역 코드 목록. 정책 목록 API(PolicyResponse.regions)와 같은 17개 시/도 코드이며, 빈 배열이면 전국 정책입니다.",
                            example = "[\"GYEONGNAM\", \"ULSAN\"]"),
                    schema = @Schema(allowableValues = {"SEOUL", "BUSAN", "DAEGU", "INCHEON", "GWANGJU", "DAEJEON", "ULSAN", "SEJONG",
                            "GYEONGGI", "GANGWON", "CHUNGBUK", "CHUNGNAM", "JEONBUK", "JEONNAM", "GYEONGBUK", "GYEONGNAM", "JEJU"}))
            List<String> regions
    ) {
        static Source from(Policy policy) {
            return new Source(policy.getPolicyId(), policy.getName(), policy.regionCodes());
        }
    }
}
