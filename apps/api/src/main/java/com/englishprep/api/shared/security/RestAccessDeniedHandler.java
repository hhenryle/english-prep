package com.englishprep.api.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Called by Spring Security when a logged-in caller is refused by an access rule (for example a path
 * that will need a role). Like {@link RestAuthenticationEntryPoint}, it hands the exception to the
 * {@code @RestControllerAdvice} so the 403 has the same JSON format as every other error.
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public RestAccessDeniedHandler(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        if (resolver.resolveException(request, response, null, exception) == null) {
            response.sendError(HttpStatus.FORBIDDEN.value());
        }
    }
}
