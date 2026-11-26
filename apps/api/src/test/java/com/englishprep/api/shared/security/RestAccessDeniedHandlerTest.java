package com.englishprep.api.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.ModelAndView;

/** Plain unit test with a fake resolver standing in for the MVC exception handling. */
class RestAccessDeniedHandlerTest {

    @Test
    void passesTheExceptionToTheResolver() throws Exception {
        Exception[] received = new Exception[1];
        var handler = new RestAccessDeniedHandler((request, response, h, ex) -> {
            received[0] = ex;
            return new ModelAndView();
        });
        AccessDeniedException denied = new AccessDeniedException("no");

        handler.handle(new MockHttpServletRequest(), new MockHttpServletResponse(), denied);

        assertThat(received[0]).isSameAs(denied);
    }

    @Test
    void answers403WhenNobodyHandlesIt() throws Exception {
        var handler = new RestAccessDeniedHandler((request, response, h, ex) -> null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("no"));

        assertThat(response.getStatus()).isEqualTo(403);
    }
}
