package com.englishprep.api.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Called by Spring Security when an anonymous caller reaches a protected path. Security runs before
 * Spring MVC, so MVC's error handling does not see this case by itself. This class passes the
 * exception to the same {@code @RestControllerAdvice} that handles every other error, so the 401
 * has the same JSON format as the rest of the API.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final HandlerExceptionResolver resolver;

    public RestAuthenticationEntryPoint(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        if (resolver.resolveException(request, response, null, exception) == null) {
            // Nobody handled it: fall back to a bare 401 so the caller never gets a 200 by mistake.
            response.sendError(HttpStatus.UNAUTHORIZED.value());
        }
    }
}
