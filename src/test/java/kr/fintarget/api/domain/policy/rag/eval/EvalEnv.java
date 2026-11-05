package kr.fintarget.api.domain.policy.rag.eval;

import java.util.Arrays;
import java.util.List;

/**
 * 평가 실행기 환경 변수 읽기 (#184).
 */
final class EvalEnv {

    private EvalEnv() {
    }

    static String get(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /** 쉼표 구분 값 (빈 값 제외) */
    static List<String> csv(String name, String defaultValue) {
        return Arrays.stream(get(name, defaultValue).split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
