package com.englishprep.api.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.englishprep.api.shared.security.AuthenticatedMember;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/** Unit test: puts different callers in the security context and checks the id that is recorded. */
class CurrentAuditorTest {

    private final CurrentAuditor auditor = new CurrentAuditor();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void callerIs(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void aLoggedInMemberIsRecordedByItsId() {
        callerIs(UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedMember(7L), null, List.of()));

        assertThat(auditor.getCurrentAuditor()).contains(7L);
    }

    @Test
    void withNoCallerTheSystemIsRecorded() {
        assertThat(auditor.getCurrentAuditor()).contains(JpaAuditingConfig.SYSTEM_USER_ID).contains(0L);
    }

    @Test
    void anAnonymousCallerIsRecordedAsTheSystem() {
        callerIs(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(auditor.getCurrentAuditor()).contains(0L);
    }

    @Test
    void aPrincipalOfAnotherKindIsRecordedAsTheSystem() {
        callerIs(UsernamePasswordAuthenticationToken.authenticated("someone", null, List.of()));

        assertThat(auditor.getCurrentAuditor()).contains(0L);
    }

    @Test
    void aMemberThatIsNotAuthenticatedIsRecordedAsTheSystem() {
        callerIs(UsernamePasswordAuthenticationToken.unauthenticated(new AuthenticatedMember(7L), null));

        assertThat(auditor.getCurrentAuditor()).contains(0L);
    }

    @Test
    void theCallerIsReadAgainOnEveryCall() {
        callerIs(UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedMember(7L), null, List.of()));
        assertThat(auditor.getCurrentAuditor()).contains(7L);

        callerIs(UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedMember(9L), null, List.of()));
        assertThat(auditor.getCurrentAuditor()).contains(9L);
    }
}
