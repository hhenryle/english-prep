package com.englishprep.api.identity.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.englishprep.api.identity.application.token.AccessToken;
import com.englishprep.api.identity.application.token.AccessTokenService;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit test of the filter alone: a fake token service accepts only the text "good-token" (member 7),
 * and each test looks at what the filter put in the security context.
 */
class BearerTokenFilterTest {

    private final AccessTokenService accessTokens = new AccessTokenService() {
        @Override
        public AccessToken issue(long memberId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Long> verify(String token) {
            return "good-token".equals(token) ? Optional.of(7L) : Optional.empty();
        }
    };
    private final BearerTokenFilter filter = new BearerTokenFilter(accessTokens);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** Runs the filter and reports the caller it recorded while the rest of the chain was running. */
    private Authentication callerSeenByTheChain(String authorizationHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorizationHeader != null) {
            request.addHeader("Authorization", authorizationHeader);
        }
        Authentication[] seen = new Authentication[1];
        boolean[] continued = new boolean[1];
        FilterChain chain = (req, res) -> {
            seen[0] = SecurityContextHolder.getContext().getAuthentication();
            continued[0] = true;
        };
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(continued[0]).as("the request always continues").isTrue();
        return seen[0];
    }

    @Test
    void aValidTokenIdentifiesTheMember() throws Exception {
        Authentication caller = callerSeenByTheChain("Bearer good-token");

        assertThat(caller).isNotNull();
        assertThat(caller.isAuthenticated()).isTrue();
        assertThat(caller.getPrincipal()).isEqualTo(new AuthenticatedMember(7L));
        assertThat(caller.getAuthorities()).isEmpty();
    }

    @Test
    void theSchemeNameIsCaseInsensitiveAndSurroundingSpacesAreIgnored() throws Exception {
        assertThat(callerSeenByTheChain("bearer good-token").getPrincipal()).isEqualTo(new AuthenticatedMember(7L));
        assertThat(callerSeenByTheChain("BEARER   good-token  ").getPrincipal())
                .isEqualTo(new AuthenticatedMember(7L));
    }

    @Test
    void noHeaderLeavesTheCallerAnonymous() throws Exception {
        assertThat(callerSeenByTheChain(null)).isNull();
    }

    @Test
    void aBadTokenLeavesTheCallerAnonymousButTheRequestStillContinues() throws Exception {
        assertThat(callerSeenByTheChain("Bearer wrong-token")).isNull();
    }

    @Test
    void anotherSchemeOrAnEmptyTokenIsIgnored() throws Exception {
        assertThat(callerSeenByTheChain("Basic dXNlcjpwYXNz")).isNull();
        assertThat(callerSeenByTheChain("Bearer")).isNull();
        assertThat(callerSeenByTheChain("Bearer    ")).isNull();
        assertThat(callerSeenByTheChain("good-token")).isNull();
    }
}
