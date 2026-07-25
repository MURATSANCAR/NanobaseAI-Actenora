package com.nanobaseai.actenora.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityHardeningFilterTest {

    @Test
    void secureHeadersAreApplied() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/transcripts");
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertTrue(response.getHeader("Strict-Transport-Security").contains("max-age="));
        assertTrue(response.getHeader("Content-Security-Policy").contains("frame-ancestors 'none'"));
    }

    @Test
    void rateLimitRejectsExcessTraffic() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(true, 2, 60);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/transcripts");
        request.setRemoteAddr("10.0.0.9");

        MockHttpServletResponse ok1 = new MockHttpServletResponse();
        filter.doFilter(request, ok1, new MockFilterChain());
        assertEquals(200, ok1.getStatus());

        MockHttpServletResponse ok2 = new MockHttpServletResponse();
        filter.doFilter(request, ok2, new MockFilterChain());
        assertEquals(200, ok2.getStatus());

        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(request, limited, new MockFilterChain());
        assertEquals(429, limited.getStatus());
        assertEquals("60", limited.getHeader("Retry-After"));
    }
}
