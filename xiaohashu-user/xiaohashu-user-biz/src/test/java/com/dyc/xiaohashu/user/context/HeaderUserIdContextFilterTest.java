package com.dyc.xiaohashu.user.context;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HeaderUserIdContextFilterTest {

    private final HeaderUserIdContextFilter filter = new HeaderUserIdContextFilter();

    @Test
    void shouldExposeUserIdFromHeaderDuringRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderUserIdContextFilter.USER_ID_HEADER, "123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) ->
                assertEquals(123L, LoginUserContextHolder.getUserId());

        filter.doFilter(request, response, chain);

        assertNull(LoginUserContextHolder.getUserId());
    }

    @Test
    void shouldIgnoreMissingUserIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) ->
                assertNull(LoginUserContextHolder.getUserId());

        filter.doFilter(request, response, chain);
    }
}
