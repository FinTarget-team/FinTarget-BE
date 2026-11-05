package kr.fintarget.api.domain.policy.search.eval;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import kr.fintarget.api.domain.policy.entity.Policy;

import java.util.Objects;

/**
 * 평가셋의 정책 식별자 (#184). 정책 UUID는 DB마다 달라 출처와 외부 ID로 가리킨다.
 * 문자열 형식은 "출처:외부ID"이며 첫 번째 ':'에서 나눈다 (출처는 YOUTH_CENTER, KINFA라 ':'가 없고, 외부 ID에는 '|' 등이 들어갈 수 있다).
 */
public record PolicyKey(String source, String externalId) {

    public PolicyKey {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(externalId, "externalId");
        if (source.isBlank() || source.contains(":") || externalId.isBlank()) {
            throw new IllegalArgumentException("정책 키 형식이 아닙니다: " + source + ":" + externalId);
        }
    }

    @JsonCreator
    public static PolicyKey parse(String value) {
        int colon = value == null ? -1 : value.indexOf(':');
        if (colon < 1) {
            throw new IllegalArgumentException("정책 키는 \"출처:외부ID\" 형식이어야 합니다: " + value);
        }
        return new PolicyKey(value.substring(0, colon), value.substring(colon + 1));
    }

    public static PolicyKey of(Policy policy) {
        return new PolicyKey(policy.getSource(), policy.getExternalId());
    }

    @JsonValue
    @Override
    public String toString() {
        return source + ":" + externalId;
    }
}
