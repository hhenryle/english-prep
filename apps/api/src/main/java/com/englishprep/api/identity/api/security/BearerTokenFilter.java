package com.englishprep.api.identity.api.security;

import com.englishprep.api.identity.application.token.AccessTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads {@code Authorization: Bearer <token>} and, when the token is valid, tells Spring Security who
 * the caller is. It never rejects a request itself: with no token or a bad token it just leaves the
 * caller anonymous, and the rules in SecurityConfig then decide (public path: continue, protected
 * path: 401). That is why a stale token sent to the login endpoint does not break the login.
 */
public class BearerTokenFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final AccessTokenService accessTokens;

    public BearerTokenFilter(AccessTokenService accessTokens) {
        this.accessTokens = accessTokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = bearerToken(request);
        if (token != null) {
            accessTokens.verify(token).ifPresent(memberId -> authenticate(new AuthenticatedMember(memberId)));
        }
        chain.doFilter(request, response);
    }

    /** The text after "Bearer " (the scheme name is case-insensitive), or null when there is none. */
    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return null;
        }
        String token = header.substring(PREFIX.length()).strip();
        return token.isEmpty() ? null : token;
    }

    /** Stores the caller for the rest of this request. Spring Security clears it when the request ends. */
    private static void authenticate(AuthenticatedMember member) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(member, null, List.of()));
        SecurityContextHolder.setContext(context);
    }
}
