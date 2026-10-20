package kr.fintarget.api.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class AdminApiKeyFilterTest {

    private static final String ADMIN_KEY = "test-admin-key";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 관리자_키가_일치하면_ADMIN_권한으로_통과시킨다() throws Exception {
        MockHttpServletRequest request = adminRequest(ADMIN_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new AdminApiKeyFilter(ADMIN_KEY).doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    void 관리자_키가_다르면_403을_반환한다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new AdminApiKeyFilter(ADMIN_KEY).doFilter(adminRequest("wrong-key"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("관리자 권한이 필요합니다.");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void 관리자_키_헤더가_없으면_403을_반환한다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new AdminApiKeyFilter(ADMIN_KEY).doFilter(adminRequest(null), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void 서버에_관리자_키가_설정되지_않으면_빈_헤더도_거부한다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new AdminApiKeyFilter("").doFilter(adminRequest(""), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("관리자 API 키가 서버에 설정되어 있지 않습니다.");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void 관리자_경로가_아니면_검사하지_않는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/policies");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new AdminApiKeyFilter(ADMIN_KEY).doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletRequest adminRequest(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/policy/sync");
        if (key != null) {
            request.addHeader(AdminApiKeyFilter.HEADER_NAME, key);
        }
        return request;
    }
}
