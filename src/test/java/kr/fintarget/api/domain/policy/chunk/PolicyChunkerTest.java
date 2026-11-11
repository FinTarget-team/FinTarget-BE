package kr.fintarget.api.domain.policy.chunk;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyText;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyChunkerTest {

    private static final String YC_HEADER = """
            정책명: 청년 월세 지원
            신청 가능 지역: 서울, 경기
            나이: 만 19세 ~ 만 34세
            """;

    private final PolicyChunker chunker = new PolicyChunker(800, 100);

    private static Policy youthCenter(String description, PolicyText text) {
        return youthCenter(description, "SEOUL,GYEONGGI", text);
    }

    private static Policy youthCenter(String description, String region, PolicyText text) {
        return Policy.create("청년 월세 지원", description, 19, 34, null, null, PolicyType.HOUSING,
                region, "P-1", "YOUTH_CENTER", text);
    }

    private static Policy kinfa(String description, PolicyText text) {
        return Policy.create("햇살론", description, null, null, null, null, PolicyType.FINANCE_WELFARE,
                null, "K-1", "KINFA", text);
    }

    // 머리말을 뺀 본문
    private static String body(PolicyChunks.Draft draft) {
        return draft.content().substring(YC_HEADER.length() + 1);
    }

    @Test
    void 짧은_정책은_머리말과_섹션_블록을_담은_청크_1개로_만든다() {
        PolicyText text = PolicyText.builder()
                .supportContent("월 최대 20만원 지원")
                .applyMethod("온라인 신청(https://apply.example.com) 후 서류 제출")
                .applyPeriod("20260101 ~ 20261231")
                .referenceUrl("https://example.com/ref")
                .build();

        PolicyChunks result = chunker.chunk(youthCenter("청년 월세를 지원하는 사업", text));

        assertThat(result.chunks()).hasSize(1);
        PolicyChunks.Draft chunk = result.chunks().getFirst();
        assertThat(chunk.seq()).isZero();
        assertThat(chunk.sectionLabel()).isEqualTo(PolicyChunker.WHOLE_POLICY_LABEL);
        assertThat(chunk.content()).isEqualTo("""
                정책명: 청년 월세 지원
                신청 가능 지역: 서울, 경기
                나이: 만 19세 ~ 만 34세

                [정책 설명]
                청년 월세를 지원하는 사업

                [지원 내용]
                월 최대 20만원 지원

                [신청 방법]
                온라인 신청 후 서류 제출

                [신청 기간]
                20260101 ~ 20261231""");
    }

    @Test
    void 본문이_800자를_넘으면_섹션_경계에서_순서대로_나눈다() {
        PolicyText text = PolicyText.builder()
                .supportContent("나".repeat(300))
                .eligibilityDetail("다".repeat(300))
                .build();

        PolicyChunks result = chunker.chunk(youthCenter("가".repeat(300), text));

        assertThat(result.chunks()).extracting(PolicyChunks.Draft::seq).containsExactly(0, 1);
        assertThat(result.chunks()).extracting(PolicyChunks.Draft::sectionLabel)
                .containsExactly("정책 설명,지원 내용", "자격 조건");
        assertThat(result.chunks().get(0).content())
                .isEqualTo(YC_HEADER + "\n[정책 설명]\n" + "가".repeat(300) + "\n\n[지원 내용]\n" + "나".repeat(300));
        assertThat(result.chunks().get(1).content()).isEqualTo(YC_HEADER + "\n[자격 조건]\n" + "다".repeat(300));
        assertThat(result.chunks()).allSatisfy(chunk -> assertThat(body(chunk).length()).isLessThanOrEqualTo(800));
    }

    @Test
    void 본문이_정확히_800자면_나누지_않는다() {
        // "[정책 설명]\n"(8자) + 792자 = 800자
        PolicyChunks result = chunker.chunk(youthCenter("가".repeat(792), null));

        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunks().getFirst().sectionLabel()).isEqualTo(PolicyChunker.WHOLE_POLICY_LABEL);
    }

    @Test
    void 혼자_800자를_넘는_섹션은_문장_경계에서_나누고_앞_조각_끝을_겹쳐_넣는다() {
        StringBuilder sentences = new StringBuilder();
        for (int i = 1; i <= 60; i++) {
            sentences.append("지원 조건 ").append(i).append("번째 문장은 이렇게 설명합니다. ");
        }
        String support = sentences.toString().strip();
        PolicyText text = PolicyText.builder().supportContent(support).build();

        PolicyChunks result = chunker.chunk(youthCenter("짧은 설명", text));

        List<PolicyChunks.Draft> chunks = result.chunks();
        // 앞 섹션은 따로 끊기고, 긴 섹션 조각들은 각자 [지원 내용] 라벨을 단다
        assertThat(chunks.getFirst().sectionLabel()).isEqualTo("정책 설명");
        List<PolicyChunks.Draft> pieces = chunks.subList(1, chunks.size());
        assertThat(pieces).hasSizeGreaterThan(1);
        assertThat(pieces).allSatisfy(piece -> {
            assertThat(piece.sectionLabel()).isEqualTo("지원 내용");
            assertThat(piece.content()).startsWith(YC_HEADER + "\n[지원 내용]\n");
            assertThat(body(piece).length()).isLessThanOrEqualTo(800);
        });
        for (int i = 0; i < pieces.size() - 1; i++) {
            String current = body(pieces.get(i)).substring("[지원 내용]\n".length());
            String next = body(pieces.get(i + 1)).substring("[지원 내용]\n".length());
            assertThat(current).endsWith("설명합니다.");
            // 다음 조각 앞부분이 앞 조각 끝 100자 안에서 시작한다
            String nextHead = next.substring(0, 20);
            assertThat(current.substring(current.length() - 100)).contains(nextHead.substring(0, 10));
            assertThat(current).contains(nextHead);
        }
        // 모든 문장이 어느 조각에든 들어 있다
        for (int i = 1; i <= 60; i++) {
            String sentence = "지원 조건 " + i + "번째 문장은 이렇게 설명합니다.";
            assertThat(pieces).anySatisfy(piece -> assertThat(piece.content()).contains(sentence));
        }
    }

    @Test
    void 긴_섹션은_문장_끝보다_줄바꿈_경계를_먼저_쓴다() {
        StringBuilder lines = new StringBuilder();
        for (int i = 1; i <= 40; i++) {
            lines.append("항목 ").append(i).append(": 지원 대상 안내. 세부 내용은 공고문 참고 줄끝\n");
        }
        PolicyText text = PolicyText.builder().supportContent(lines.toString()).build();

        List<PolicyChunks.Draft> chunks = chunker.chunk(youthCenter(null, text)).chunks();

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content()).endsWith("줄끝"));
    }

    @Test
    void 경계가_없는_긴_섹션은_길이에서_자르고_빠지는_글자가_없다() {
        String noBoundary = "가".repeat(1000) + "나".repeat(1000);

        List<String> pieces = chunker.splitLongText(noBoundary, 790);

        assertThat(pieces).allSatisfy(piece -> assertThat(piece.length()).isLessThanOrEqualTo(790));
        assertThat(pieces.getFirst()).hasSize(790);
        assertThat(String.join("", pieces).replace("가", "").replace("나", "")).isEmpty();
        assertThat(pieces.getLast()).endsWith("나".repeat(10));
        assertThat(pieces.stream().mapToInt(String::length).sum()).isGreaterThanOrEqualTo(noBoundary.length());
    }

    @Test
    void KINFA는_800자를_넘어도_청크_1개로_만든다() {
        PolicyChunks result = chunker.chunk(kinfa("연소득 조건 ".repeat(80),
                PolicyText.builder().supportContent("대출 한도: 1000만원\n".repeat(40)).build()));

        assertThat(result.chunks()).hasSize(1);
        PolicyChunks.Draft chunk = result.chunks().getFirst();
        assertThat(chunk.sectionLabel()).isEqualTo(PolicyChunker.WHOLE_POLICY_LABEL);
        assertThat(chunk.content().length()).isGreaterThan(800);
        assertThat(chunk.content())
                .startsWith("정책명: 햇살론\n신청 가능 지역: 전국\n\n[지원 대상 상세 조건]\n")
                .contains("\n\n[지원 내용]\n대출 한도: 1000만원")
                .doesNotContain("나이:", "[정책 설명]");
    }

    @Test
    void 내용이_빈_섹션과_URL만_있는_섹션은_건너뛴다() {
        PolicyText text = PolicyText.builder()
                .supportContent("   ")
                .etcNote("https://only-url.example.com")
                .requiredDocuments("임대차계약서")
                .build();

        String content = chunker.chunk(youthCenter(null, text)).chunks().getFirst().content();

        assertThat(content).isEqualTo(YC_HEADER + "\n[제출 서류]\n임대차계약서")
                .doesNotContain("[지원 내용]", "[기타 참고 사항]", "[정책 설명]", "http");
    }

    @Test
    void 원문이_없는_정책도_머리말만으로_청크_1개를_만든다() {
        Policy policy = Policy.create("원문 없는 정책", null, null, null, null, null, PolicyType.ETC,
                null, "P-9", "YOUTH_CENTER", null);

        PolicyChunks result = chunker.chunk(policy);

        assertThat(result.chunks()).singleElement().satisfies(chunk -> {
            assertThat(chunk.sectionLabel()).isEqualTo(PolicyChunker.WHOLE_POLICY_LABEL);
            assertThat(chunk.content()).isEqualTo("정책명: 원문 없는 정책\n신청 가능 지역: 전국");
        });
    }

    @Test
    void 원문과_분할_규칙이_같으면_해시가_같고_바뀌면_해시가_바뀐다() {
        PolicyText text = PolicyText.builder().supportContent("월 최대 20만원 지원").build();
        String base = chunker.chunk(youthCenter("설명", text)).sourceHash();

        assertThat(base).hasSize(64);
        assertThat(chunker.chunk(youthCenter("설명", text)).sourceHash()).isEqualTo(base);
        assertThat(chunker.chunk(youthCenter("설명", PolicyText.builder().supportContent("월 최대 30만원 지원").build()))
                .sourceHash()).isNotEqualTo(base);
        assertThat(chunker.chunk(youthCenter("설명", "BUSAN", text)).sourceHash()).isNotEqualTo(base);
        assertThat(new PolicyChunker(600, 100).chunk(youthCenter("설명", text)).sourceHash()).isNotEqualTo(base);
    }

    @Test
    void 분할_설정값이_범위를_벗어나면_기동하지_않는다() {
        assertThatThrownBy(() -> new PolicyChunker(50, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PolicyChunker(800, 200)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PolicyChunker(800, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
