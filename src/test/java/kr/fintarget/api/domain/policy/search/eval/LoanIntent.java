package kr.fintarget.api.domain.policy.search.eval;

import java.util.List;

/**
 * 질문에 대출 의도가 있는지 판정한다 (#184 평가 전용). KINFA 조건부 제외 비교에만 쓰고, 운영 코드는 쓰지 않는다.
 *
 * <p>평가셋의 {@code loanIntent}·{@code type}은 사람이 적은 값이고, 이 판정기는 질문 문장만 보고
 * 같은 결론이 나오는지 확인하기 위한 것이다. 운영에 넣을지는 비교 결과를 보고 정한다.
 */
public final class LoanIntent {

    /**
     * 대출 의도를 뜻하는 표현. 띄어쓰기를 지운 질문에서 부분 문자열로 찾는다.
     *
     * <p>넣지 않은 것과 이유
     * <ul>
     *   <li>"보증": 이 도메인에서 대출 의도와 무관하게 너무 자주 쓰인다 — {@code 전세보증금반환보증 보증료 지원},
     *       {@code 전세자금보증}, {@code 청년 전월세 대출보증}처럼 지원 정책 이름에 들어간다.
     *       {@code 보증금}·{@code 보증료}를 뒤 글자로 걸러도 {@code 반환보증}처럼 복합어 안의 "보증"이 남는다.
     *       대출을 찾는 질문은 보통 "대출"·"융자"·"빌리"를 함께 쓰므로 빼는 쪽이 오탐이 적다.
     *       대신 "특례보증이 뭐야"처럼 "보증"만 쓴 질문은 놓친다 ({@link #MISSED_WITHOUT_GUARANTEE})</li>
     *   <li>"부담", "모자라", "돈이 없어": 지원금을 찾는 질문과 구분되지 않아 KINFA 제외가 무력해진다</li>
     * </ul>
     */
    public static final List<String> KEYWORDS = List.of(
            "대출", "융자", "빌리", "빌릴", "빌려", "차입",
            "상환", "금리", "이자", "한도"
    );

    /** "보증"을 뺐기 때문에 놓치는 형태. 평가 결과를 보고 넣을지 정한다 */
    public static final List<String> MISSED_WITHOUT_GUARANTEE = List.of("특례보증", "신용보증", "보증서");

    private LoanIntent() {
    }

    public static boolean of(String question) {
        return !matched(question).isEmpty();
    }

    /** 판정에 걸린 표현 (리포트에 남겨 오탐을 눈으로 확인한다) */
    public static List<String> matched(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        String normalized = question.replace(" ", "");
        return KEYWORDS.stream().filter(normalized::contains).toList();
    }
}
