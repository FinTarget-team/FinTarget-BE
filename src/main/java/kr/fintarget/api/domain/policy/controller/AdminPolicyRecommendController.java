package kr.fintarget.api.domain.policy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.fintarget.api.common.ApiResponse;
import kr.fintarget.api.domain.policy.recommend.LlmPolicyRecommendationSelector;
import kr.fintarget.api.domain.policy.recommend.rerank.PolicyRerankCache;
import kr.fintarget.api.domain.policy.recommend.rerank.PolicyRerankPrecomputer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자용 추천 리랭킹 운영 API (#229). X-Admin-Key 헤더가 ADMIN_API_KEY와 일치해야 호출할 수 있다 (AdminApiKeyFilter).
 */
@Tag(name = "정책 관리(운영)")
@SecurityRequirement(name = "adminApiKey")
@RestController
@RequestMapping("/api/admin/policy/recommend/rerank")
@RequiredArgsConstructor
public class AdminPolicyRecommendController {

    private final LlmPolicyRecommendationSelector selector;
    private final PolicyRerankPrecomputer precomputer;
    private final PolicyRerankCache cache;

    @Operation(
            summary = "추천 리랭킹 전체 사용자 미리 계산",
            description = "온보딩을 마친 전체 사용자의 맞춤 정책 목록을 LLM으로 미리 골라 메모리 캐시에 넣습니다. "
                    + "앱 시작 후와 동기화 후 조건 추출이 끝난 뒤에도 자동으로 실행됩니다. "
                    + "비동기로 실행하고 바로 응답하며, 진행 상황은 서버 로그(\"추천 리랭킹\")로 확인합니다. "
                    + "입력(사용자 정보·후보 정책·프롬프트 버전)이 같은 캐시가 있는 사용자는 LLM을 호출하지 않습니다. "
                    + "이미 실행 중이면 끝난 뒤 한 번 더 실행합니다. 리랭킹이 꺼져 있거나 AI 키가 없으면 실행하지 않습니다(started=false)."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<RerankStatus>> precomputeAll() {
        boolean started = precomputer.requestAll();
        return ResponseEntity.ok(ApiResponse.ok(status(started)));
    }

    @Operation(
            summary = "추천 리랭킹 켜기/끄기 (재시작 없이)",
            description = "실행 중인 서버에서 바로 리랭킹을 켜거나 끕니다. 끄면 LLM을 호출하지 않고 규칙 기반 상위 N건을 반환합니다. "
                    + "메모리에만 반영되어 재시작(배포)하면 설정값 policy.recommend.rerank.enabled(환경 변수 POLICY_RECOMMEND_RERANK_ENABLED)로 돌아갑니다. "
                    + "켜도 캐시는 다음 조회·미리 계산 때 채워집니다."
    )
    @PutMapping("/enabled")
    public ResponseEntity<ApiResponse<RerankStatus>> setEnabled(
            @Parameter(description = "true면 켜고 false면 끔", required = true)
            @RequestParam boolean enabled) {
        selector.setEnabled(enabled);
        return ResponseEntity.ok(ApiResponse.ok(status(false)));
    }

    private RerankStatus status(boolean started) {
        return new RerankStatus(started, selector.isEnabled(), selector.isAvailable(), precomputer.isRunningAll(),
                cache.size(), selector.promptVersion(), selector.modelId());
    }

    @Schema(description = "추천 리랭킹 상태")
    public record RerankStatus(
            @Schema(description = "이번 요청으로 전체 미리 계산을 예약했는지") boolean started,
            @Schema(description = "리랭킹 스위치 (설정값 또는 관리자 API로 바꾼 값)") boolean enabled,
            @Schema(description = "실제로 LLM 리랭킹을 쓰는지 (스위치가 켜져 있고 AI 키가 있음)") boolean available,
            @Schema(description = "전체 미리 계산이 진행 중인지") boolean precomputing,
            @Schema(description = "캐시된 사용자 수") int cachedUsers,
            @Schema(description = "프롬프트 버전", example = "policy-rerank-v1") String promptVersion,
            @Schema(description = "리랭킹 모델 (openai.model.premium)", example = "gpt-6.1-sol") String model
    ) {
    }
}
