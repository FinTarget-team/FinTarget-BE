package kr.fintarget.api.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class BankWebhookKeyFilterTest {

    private static final String WEBHOOK_KEY = "test-webhook-key";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 웹훅_키가_일치하면_BANK_WEBHOOK_권한으로_통과시킨다() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        new BankWebhookKeyFilter(WEBHOOK_KEY).doFilter(webhookRequest(WEBHOOK_KEY), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_BANK_WEBHOOK");
    }

    @Test
    void 웹훅_키_앞뒤_공백은_무시한다() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        new BankWebhookKeyFilter(WEBHOOK_KEY).doFilter(webhookRequest("  " + WEBHOOK_KEY + " "), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void 웹훅_키가_다르면_403을_반환한다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new BankWebhookKeyFilter(WEBHOOK_KEY).doFilter(webhookRequest("wrong-key"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("웹훅 키가 올바르지 않습니다.");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void 웹훅_키_헤더가_없으면_403을_반환한다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new BankWebhookKeyFilter(WEBHOOK_KEY).doFilter(webhookRequest(null), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void 서버에_웹훅_키가_설정되지_않으면_빈_헤더도_거부한다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new BankWebhookKeyFilter("").doFilter(webhookRequest(""), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("웹훅 키가 서버에 설정되어 있지 않습니다.");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void 웹훅_경로가_아니면_검사하지_않는다() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        new BankWebhookKeyFilter(WEBHOOK_KEY).doFilter(new MockHttpServletRequest("POST", "/api/expenses/sync"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletRequest webhookRequest(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/webhooks/bank-transactions");
        if (key != null) {
            request.addHeader(BankWebhookKeyFilter.HEADER_NAME, key);
        }
        return request;
    }
}
