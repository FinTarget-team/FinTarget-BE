package kr.fintarget.api.domain.policy.chunk;

import kr.fintarget.api.domain.ai.chat.service.AiAnswerFormatter;
import kr.fintarget.api.domain.policy.document.PolicySections;
import kr.fintarget.api.domain.policy.entity.Policy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 정책 1건을 검색용 청크로 나눈다 (#180).
 *
 * <ul>
 *   <li>정책 1건 = 청크 1개가 기본이다. 섹션은 PolicySections 순서대로 "[라벨]\n본문" 블록으로 이어 붙인다</li>
 *   <li>온통청년 정책의 본문(머리말을 뺀 블록 합계)이 maxLength를 넘을 때만 섹션 경계에서 나눈다.
 *       섹션을 순서대로 담다가 maxLength를 넘기 전에 끊는다</li>
 *   <li>섹션 하나가 혼자 maxLength를 넘으면 그 섹션만 줄바꿈 → 문장 끝 → 공백 경계에서 나누고,
 *       앞 조각 끝 overlap자 정도를 다음 조각 앞에 겹쳐 넣는다</li>
 *   <li>KINFA는 길이와 상관없이 나누지 않는다 (원문이 짧은 값 여러 개로 된 대출 상품 1건)</li>
 *   <li>모든 청크 앞에 정책명·신청 가능 지역·나이(정형 값이 있을 때) 머리말을 붙인다.
 *       원문 섹션이 하나도 없어도 머리말만으로 청크 1개를 만든다</li>
 *   <li>본문의 URL은 단건 AI 질문 문서(PolicyDocument)와 같이 지운다 (#147)</li>
 * </ul>
 */
@Component
public class PolicyChunker {

    /** 분할 규칙을 바꾸면 올린다. 해시에 들어가므로 올리면 다음 갱신 때 전체 청크가 다시 만들어진다 */
    public static final String CHUNKER_VERSION = "policy-chunk-v1";
    /** 정책 전체가 청크 1개일 때의 section_label */
    public static final String WHOLE_POLICY_LABEL = "전체";
    static final int DEFAULT_MAX_LENGTH = 800;
    static final int DEFAULT_OVERLAP = 100;

    private static final String BLOCK_SEPARATOR = "\n\n";

    private final int maxLength;
    private final int overlap;

    public PolicyChunker(@Value("${policy.chunk.max-length:" + DEFAULT_MAX_LENGTH + "}") int maxLength,
                         @Value("${policy.chunk.overlap:" + DEFAULT_OVERLAP + "}") int overlap) {
        if (maxLength < 100) {
            throw new IllegalArgumentException("policy.chunk.max-length는 100 이상이어야 합니다: " + maxLength);
        }
        if (overlap < 0 || overlap >= maxLength / 4) {
            throw new IllegalArgumentException("policy.chunk.overlap은 0 이상, max-length의 1/4 미만이어야 합니다: " + overlap);
        }
        this.maxLength = maxLength;
        this.overlap = overlap;
    }

    public PolicyChunks chunk(Policy policy) {
        String header = header(policy);
        List<Block> blocks = blocks(policy);

        List<Body> bodies = PolicySections.isKinfa(policy) || bodyLength(blocks) <= maxLength
                ? List.of(new Body(WHOLE_POLICY_LABEL, join(blocks)))
                : splitBySections(blocks);

        List<PolicyChunks.Draft> drafts = new ArrayList<>();
        for (Body body : bodies) {
            String content = body.text().isEmpty() ? header.strip() : header + "\n" + body.text();
            drafts.add(new PolicyChunks.Draft(drafts.size(), body.label(), content));
        }
        return new PolicyChunks(drafts, hash(drafts));
    }

    private static String header(Policy policy) {
        StringBuilder header = new StringBuilder();
        header.append("정책명: ").append(policy.getName()).append('\n');
        header.append("신청 가능 지역: ").append(PolicySections.regionLabel(policy)).append('\n');
        String ageLabel = PolicySections.ageLabel(policy);
        if (ageLabel != null) {
            header.append("나이: ").append(ageLabel).append('\n');
        }
        return header.toString();
    }

    // URL을 지우고 남은 내용이 없는 섹션은 뺀다
    private static List<Block> blocks(Policy policy) {
        List<Block> blocks = new ArrayList<>();
        for (PolicySections.Section section : PolicySections.of(policy)) {
            String value = AiAnswerFormatter.removeUrls(section.value().strip());
            if (value != null) {
                blocks.add(new Block(section.label(), value));
            }
        }
        return blocks;
    }

    // 섹션을 순서대로 담다가 다음 섹션을 넣으면 maxLength를 넘을 때 끊는다
    private List<Body> splitBySections(List<Block> blocks) {
        List<Body> bodies = new ArrayList<>();
        List<Block> current = new ArrayList<>();
        for (Block block : blocks) {
            if (block.text().length() > maxLength) {
                flush(bodies, current);
                for (String piece : splitLongText(block.value(), maxLength - block.prefix().length())) {
                    bodies.add(new Body(block.label(), block.prefix() + piece));
                }
                continue;
            }
            int lengthWithBlock = bodyLength(current) + (current.isEmpty() ? 0 : BLOCK_SEPARATOR.length()) + block.text().length();
            if (lengthWithBlock > maxLength) {
                flush(bodies, current);
            }
            current.add(block);
        }
        flush(bodies, current);
        return bodies;
    }

    private static void flush(List<Body> bodies, List<Block> current) {
        if (current.isEmpty()) {
            return;
        }
        Set<String> labels = new LinkedHashSet<>();
        current.forEach(block -> labels.add(block.label()));
        bodies.add(new Body(String.join(",", labels), join(current)));
        current.clear();
    }

    /**
     * limit자 이하 조각으로 나눈다. 경계는 limit 안의 뒤쪽 절반에서 줄바꿈 → 문장 끝(. ! ? 뒤 공백) → 공백 순으로 찾고,
     * 없으면 limit에서 자른다. 다음 조각은 앞 조각 끝 overlap자 지점부터(단어 중간이면 다음 단어부터) 시작한다.
     */
    List<String> splitLongText(String text, int limit) {
        List<String> pieces = new ArrayList<>();
        int length = text.length();
        int start = 0;
        while (length - start > limit) {
            int end = boundary(text, start, start + limit);
            String piece = text.substring(start, end).strip();
            if (!piece.isEmpty()) {
                pieces.add(piece);
            }
            start = overlapStart(text, start, end);
        }
        String last = text.substring(start).strip();
        if (!last.isEmpty()) {
            pieces.add(last);
        }
        return pieces;
    }

    private static int boundary(String text, int start, int hardEnd) {
        int min = start + (hardEnd - start) / 2;
        for (int i = hardEnd - 1; i >= min; i--) {
            if (text.charAt(i) == '\n') {
                return i + 1;
            }
        }
        for (int i = hardEnd - 1; i >= min; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && Character.isWhitespace(text.charAt(i + 1))) {
                return i + 1;
            }
        }
        for (int i = hardEnd - 1; i >= min; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i + 1;
            }
        }
        return hardEnd;
    }

    // 조각 길이는 limit/2 이상이고 overlap은 limit/4 미만이므로 다음 시작점은 항상 start보다 뒤다
    private int overlapStart(String text, int start, int end) {
        int next = Math.max(start + 1, end - overlap);
        if (next > 0 && !Character.isWhitespace(text.charAt(next - 1))) {
            for (int i = next; i < end; i++) {
                if (Character.isWhitespace(text.charAt(i))) {
                    next = i + 1;
                    break;
                }
            }
        }
        while (next < end && Character.isWhitespace(text.charAt(next))) {
            next++;
        }
        return next;
    }

    private static int bodyLength(List<Block> blocks) {
        return join(blocks).length();
    }

    private static String join(List<Block> blocks) {
        return String.join(BLOCK_SEPARATOR, blocks.stream().map(Block::text).toList());
    }

    // 분할 규칙(버전·길이 설정)과 만들어진 청크 전체로 계산한다. 머리말에 들어가는 정책명·지역·나이가 바뀌어도 해시가 바뀐다
    private String hash(List<PolicyChunks.Draft> drafts) {
        StringBuilder input = new StringBuilder(CHUNKER_VERSION).append('|').append(maxLength).append('|').append(overlap);
        for (PolicyChunks.Draft draft : drafts) {
            input.append('\u0000').append(draft.sectionLabel()).append('\u0001').append(draft.content());
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    private record Block(String label, String value) {
        String prefix() {
            return "[" + label + "]\n";
        }

        String text() {
            return prefix() + value;
        }
    }

    private record Body(String label, String text) {
    }
}
