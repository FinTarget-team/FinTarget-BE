package kr.fintarget.api.domain.ai.chat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 답변 후처리 (#147). 앱은 답변을 일반 텍스트로 표시하므로 마크다운 기호와 URL을 지운다.
 * 프롬프트(policy-qa-v2)에도 같은 규칙이 있지만 모델이 항상 지키지는 않아 안전망으로 둔다.
 * 신청 링크는 답변 본문이 아니라 응답의 applyUrl로 내려준다.
 *
 * <p>목록 기호는 "- "로 통일하고, 번호 목록(1. 2.)은 절차 설명에 쓰이므로 그대로 둔다.
 * 한 개짜리 "*"는 각주 표시일 수 있어 지우지 않는다.
 */
public final class AiAnswerFormatter {

    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]\\n]*)]\\(([^)\\s]*)\\)");
    private static final Pattern URL = Pattern.compile("(?:https?://|www\\.)[^\\s)\\]>]+");
    private static final Pattern EMPHASIS = Pattern.compile("\\*\\*|__|`");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s*");
    private static final Pattern BULLET = Pattern.compile("^[*•·]\\s+");
    private static final Pattern EMPTY_BRACKETS = Pattern.compile("\\(\\s*\\)|\\[\\s*]");
    private static final Pattern SPACES = Pattern.compile("[ \\t]{2,}");
    // URL을 지운 뒤 "신청 링크:"처럼 라벨만 남은 줄 (URL이 있던 줄에만 적용)
    private static final Pattern LABEL_ONLY = Pattern.compile("^(?:- )?[^:：]{0,20}[:：]$");

    private AiAnswerFormatter() {
    }

    public static String format(String answer) {
        if (answer == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (String line : answer.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            String formatted = formatLine(line);
            if (formatted != null) {
                lines.add(formatted);
            }
        }
        return String.join("\n", lines).replaceAll("\n{3,}", "\n\n").strip();
    }

    /**
     * URL만 지운다. 프롬프트에 넣는 정책 원문용이며(모델이 원문 URL을 답변에 옮기지 않도록), 원문의 목록 기호 등은 그대로 둔다.
     * URL을 지워 "관련 사이트:"처럼 라벨만 남은 줄과 빈 괄호는 지운다. 결과가 비면 null.
     */
    static String removeUrls(String text) {
        if (text == null) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            Matcher url = URL.matcher(line);
            if (!url.find()) {
                lines.add(line);
                continue;
            }
            String removed = SPACES.matcher(EMPTY_BRACKETS.matcher(url.replaceAll("")).replaceAll("")).replaceAll(" ").strip();
            if (!removed.isEmpty() && !LABEL_ONLY.matcher(removed).matches()) {
                lines.add(removed);
            }
        }
        String joined = String.join("\n", lines).strip();
        return joined.isEmpty() ? null : joined;
    }

    // 줄 하나를 정리한다. URL을 지워 내용이 없어진 줄은 null(줄째 삭제)
    private static String formatLine(String line) {
        // 링크는 텍스트만 남긴다. 텍스트가 비어 있으면([](url)) 링크째 지운다
        String text = MARKDOWN_LINK.matcher(line).replaceAll(match -> Matcher.quoteReplacement(match.group(1)));
        Matcher url = URL.matcher(text);
        boolean hadUrl = url.find();
        text = url.replaceAll("");
        text = EMPHASIS.matcher(text).replaceAll("");
        text = EMPTY_BRACKETS.matcher(text).replaceAll("");
        text = HEADING.matcher(text.strip()).replaceFirst("");
        text = BULLET.matcher(text).replaceFirst("- ");
        text = SPACES.matcher(text).replaceAll(" ").strip();
        if (hadUrl && (text.isEmpty() || text.equals("-") || LABEL_ONLY.matcher(text).matches())) {
            return null;
        }
        return text;
    }
}
