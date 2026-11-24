package kr.fintarget.api.domain.policy.recommend.rerank;

import kr.fintarget.api.domain.policy.condition.BenefitKind;
import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyBenefit;
import kr.fintarget.api.domain.policy.entity.PolicyCondition;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.recommend.ApplyPeriodStatus;
import kr.fintarget.api.domain.policy.recommend.PolicyRecommendationOrder;
import kr.fintarget.api.domain.user.code.Region;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 리랭킹 프롬프트에 넣을 후보 정책 한 줄 요약 (#229). 원문 전체는 넣지 않고 항목마다 길이 상한을 둔다.
 * 정책 ID(UUID) 대신 후보 번호([1]~[M])를 쓴다: 입력 토큰을 줄이고, 모델이 긴 ID를 잘못 옮겨 적는 일을 막는다.
 *
 * <p>형식: {@code [번호] 정책명 | 유형 | 혜택 | 대상 | 지원 | 지역 | 신청 | 특정 대상}
 */
public final class PolicyRerankCandidateLine {

    static final int NAME_MAX = 60;
    static final int TARGET_MAX = 80;
    static final int SUPPORT_MAX = 120;
    static final int GROUP_MAX = 30;

    private PolicyRerankCandidateLine() {
    }

    public static String of(int number, Policy policy, String userRegion, LocalDate today) {
        PolicyText text = policy.getText();
        PolicyCondition condition = policy.getCondition();
        List<String> parts = new ArrayList<>();
        parts.add("[" + number + "] " + cut(policy.getName(), NAME_MAX));
        parts.add("유형: " + typeLabel(policy));
        parts.add("혜택: " + benefitLabel(condition));
        parts.add("대상: " + target(policy, text));
        parts.add("지원: " + support(policy, text));
        parts.add("지역: " + region(policy, condition));
        parts.add("신청: " + applyLabel(PolicyRecommendationOrder.rank(policy, userRegion, today).applyStatus()));
        parts.add("특정 대상: " + specialTarget(condition));
        return String.join(" | ", parts);
    }

    private static String typeLabel(Policy policy) {
        if (policy.getPolicyType() == null) {
            return "기타";
        }
        return switch (policy.getPolicyType()) {
            case HOUSING -> "주거";
            case FINANCE_WELFARE -> "금융·복지";
            case STARTUP -> "창업";
            case ETC -> "기타";
        };
    }

    private static String benefitLabel(PolicyCondition condition) {
        BenefitKind kind = condition == null ? null : condition.getBenefitKind();
        if (kind == null) {
            return "미분석";
        }
        return switch (kind) {
            case MONEY -> "MONEY(현금·적립·이자 지원)";
            case COST_SAVING -> "COST_SAVING(할인·바우처·감면)";
            case LOAN -> "LOAN(대출)";
            default -> kind.name();
        };
    }

    // 나이 범위 + 자격 조건 한 줄 (온통청년은 추가 자격 조건, KINFA는 description이 자격 조건)
    private static String target(Policy policy, PolicyText text) {
        StringBuilder sb = new StringBuilder();
        if (policy.getMinAge() != null || policy.getMaxAge() != null) {
            sb.append(policy.getMinAge() == null ? "" : policy.getMinAge())
                    .append("~")
                    .append(policy.getMaxAge() == null ? "" : policy.getMaxAge())
                    .append("세");
        }
        String detail = text == null ? null : text.getEligibilityDetail();
        if (isBlank(detail)) {
            detail = policy.getDescription();
        }
        if (!isBlank(detail)) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(cut(detail, TARGET_MAX));
        }
        return sb.isEmpty() ? "정보 없음" : sb.toString();
    }

    // 추출한 현금 혜택 금액 + 지원 내용 원문 한 줄
    private static String support(Policy policy, PolicyText text) {
        StringBuilder sb = new StringBuilder();
        String amount = amount(policy.getBenefit());
        if (amount != null) {
            sb.append(amount).append(" / ");
        }
        String content = text == null ? null : text.getSupportContent();
        if (isBlank(content)) {
            content = policy.getDescription();
        }
        sb.append(isBlank(content) ? "정보 없음" : cut(content, SUPPORT_MAX));
        return sb.toString();
    }

    private static String amount(PolicyBenefit benefit) {
        if (benefit == null || !benefit.hasAny()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (benefit.getMonthlyAmount() != null && benefit.getMonthlyAmount() > 0) {
            parts.add("월 " + won(benefit.getMonthlyAmount())
                    + (benefit.getBenefitMonths() == null ? "" : " × " + benefit.getBenefitMonths() + "개월"));
        }
        if (benefit.getOneTimeAmount() != null && benefit.getOneTimeAmount() > 0) {
            parts.add("일시금 " + won(benefit.getOneTimeAmount()));
        }
        if (parts.isEmpty()) {
            return null;
        }
        return "금액 " + String.join(" + ", parts) + (benefit.isMaximum() ? " (최대)" : "");
    }

    private static String won(long amount) {
        if (amount >= 10_000 && amount % 10_000 == 0) {
            return String.format("%,d만원", amount / 10_000);
        }
        return String.format("%,d원", amount);
    }

    private static String region(Policy policy, PolicyCondition condition) {
        List<String> codes = policy.matchingRegionCodes();
        String label = codes.isEmpty() ? "전국" : String.join("·", codes.stream().map(PolicyRerankCandidateLine::regionLabel).toList());
        Boolean structured = policy.getRegionLocalScope();
        boolean localScope = structured != null
                ? structured
                : condition != null && Boolean.TRUE.equals(condition.getLocalScope());
        return localScope ? label + "(시·군·구 한정)" : label;
    }

    private static String regionLabel(String code) {
        Region region = Region.fromCode(code);
        return region == null ? code : region.getLabel();
    }

    private static String applyLabel(ApplyPeriodStatus status) {
        return switch (status) {
            case OPEN -> "가능";
            case UNKNOWN -> "확인 필요";
            case NOT_OPEN -> "마감 또는 시작 전";
        };
    }

    private static String specialTarget(PolicyCondition condition) {
        if (condition == null || condition.getSpecialTarget() == null) {
            return "확인 안 됨";
        }
        if (!condition.isSpecialTarget()) {
            return "아님";
        }
        String group = condition.getSpecialTargetGroup();
        return isBlank(group) ? "있음" : cut(group, GROUP_MAX);
    }

    // 줄바꿈·연속 공백을 한 칸으로 줄이고, 상한을 넘으면 자르고 "…"를 붙인다. 구분자 "|"는 "/"로 바꾼다
    static String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        String flat = value.replace('|', '/').replaceAll("\\s+", " ").strip();
        if (flat.length() <= max) {
            return flat;
        }
        return flat.substring(0, max).stripTrailing() + "…";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
