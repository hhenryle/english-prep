package com.englishprep.api.shared.persistence;

import com.englishprep.api.shared.security.AuthenticatedMember;
import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tells Spring Data who is making a change, for the created_by / updated_by columns: the id of the
 * logged-in member when the current request has one, otherwise the system.
 *
 * <p>The caller is read from the Spring Security context of the current thread. Code that runs outside
 * a request (a scheduled job, an {@code @Async} task) has no caller there and is recorded as the system.
 */
class CurrentAuditor implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // Only a real, authenticated member counts. An anonymous caller has a plain String as its principal.
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedMember member) {
            return Optional.of(member.id());
        }
        return Optional.of(JpaAuditingConfig.SYSTEM_USER_ID);
    }
}
