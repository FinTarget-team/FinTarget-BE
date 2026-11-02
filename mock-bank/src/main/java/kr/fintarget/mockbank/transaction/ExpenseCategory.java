package kr.fintarget.mockbank.transaction;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 핀타겟 지출 카테고리. iOS 앱(AddExpenseView)이 보내는 6개 값과 같다 (#165).
 * 메인 서버는 카테고리를 자유 문자열로 저장하므로, label 값이 그대로 전송된다.
 */
public enum ExpenseCategory {
    FOOD("식비", 5_000, 30_000, List.of("김밥천국", "맘스터치", "한솥도시락", "본죽", "서브웨이", "교촌치킨", "스타벅스", "메가커피", "이디야커피", "컴포즈커피")),
    TRANSPORT("교통", 1_000, 5_000, List.of("서울교통공사", "티머니 버스", "카카오T 택시", "따릉이", "코레일")),
    SHOPPING("쇼핑", 10_000, 150_000, List.of("무신사", "쿠팡", "올리브영", "29CM", "지그재그")),
    LEISURE("문화/여가", 5_000, 50_000, List.of("CGV", "메가박스", "인터파크티켓", "교보문고", "YES24", "넷플릭스", "유튜브 프리미엄", "멜론")),
    LIVING("생활", 5_000, 60_000, List.of("SKT", "KT", "LG U+", "온누리약국", "동네의원", "다이소", "이마트24")),
    ETC("기타", 3_000, 50_000, List.of("경조사비", "기부", "세탁소", "택배비", "수수료"));

    private final String label;
    private final long minAmount;
    private final long maxAmount;
    private final List<String> merchants;

    ExpenseCategory(String label, long minAmount, long maxAmount, List<String> merchants) {
        this.label = label;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.merchants = merchants;
    }

    public String label() {
        return label;
    }

    public long minAmount() {
        return minAmount;
    }

    public long maxAmount() {
        return maxAmount;
    }

    public List<String> merchants() {
        return merchants;
    }

    public static List<String> labels() {
        return Arrays.stream(values()).map(ExpenseCategory::label).toList();
    }

    public static Optional<ExpenseCategory> fromLabel(String label) {
        return Arrays.stream(values()).filter(c -> c.label.equals(label)).findFirst();
    }
}
