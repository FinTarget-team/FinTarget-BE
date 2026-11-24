package kr.fintarget.api.domain.policy.rag;

import kr.fintarget.api.domain.policy.entity.Policy;
import kr.fintarget.api.domain.policy.entity.PolicyType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalPlaceMentionTest {

    private static Policy policy(String name, String description) {
        return Policy.create(name, description, null, null, null, null, PolicyType.ETC, "JEONNAM", null, null);
    }

    @Test
    void 정책명의_시군구_이름이나_앞부분이_질문에_있으면_말한_것으로_본다() {
        Policy gokseong = policy("곡성군 일하잡(job)센터 지원사업", null);
        Policy gwangyang = policy("(광양시)광양학사(서울 공공기숙사) 운영", null);
        Policy yongin = policy("용인청년 부동산 중개 보수 감면 사업", null);

        assertThat(LocalPlaceMention.mentioned(gokseong, "곡성군 일자리 지원 있어?")).isTrue();
        assertThat(LocalPlaceMention.mentioned(gokseong, "곡성에서 받을 수 있는 거")).isTrue();
        assertThat(LocalPlaceMention.mentioned(gwangyang, "광양시 기숙사 있어?")).isTrue();
        assertThat(LocalPlaceMention.mentioned(yongin, "용인에서 받을 수 있는 정책")).isTrue();
        assertThat(LocalPlaceMention.mentioned(yongin, "용인시 중개보수 지원")).isTrue();
        assertThat(LocalPlaceMention.mentioned(gokseong, "졸업하고 일자리 구할 때 도움받는 정책 있어?")).isFalse();
    }

    @Test
    void 정책명에_지명이_없으면_본문의_전체_이름이_질문에_있을_때만_인정한다() {
        Policy gangjin = policy("강진품애(愛) 청년 주거비 지원사업", "강진군에 주소를 둔 청년의 월세를 지원하며 지원시 서류를 확인한다");

        assertThat(LocalPlaceMention.mentioned(gangjin, "강진군 청년 월세 지원 있어?")).isTrue();
        assertThat(LocalPlaceMention.mentioned(gangjin, "강진 월세 지원")).isFalse();
        // 본문의 "지원시" 같은 일반 낱말은 지명이 아니다
        assertThat(LocalPlaceMention.mentioned(gangjin, "월세 지원 있어?")).isFalse();
    }

    @Test
    void 한_글자_구_이름은_전체_이름이_있을_때만_인정하고_필터는_한정_정책만_거른다() {
        Policy seogu = policy("서구 청년 천원 복비(부동산 중개보수) 지원사업", null);
        Policy national = policy("청년 월세 지원", null);

        assertThat(LocalPlaceMention.mentioned(seogu, "광주 서구 중개보수 지원")).isTrue();
        assertThat(LocalPlaceMention.mentioned(seogu, "서울 중개보수 지원")).isFalse();
        assertThat(LocalPlaceMention.filter(List.of(seogu, national), "전남 주거 지원", policy -> policy == seogu))
                .containsExactly(national);
    }
}
