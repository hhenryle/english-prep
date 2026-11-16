package com.englishprep.api.shared.persistence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Turns on JPA auditing. It lives in its own class (not on ApiApplication) so that test slices
 * that do not load JPA are not affected; tests that need it import this class.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    /** created_by / updated_by value for changes made by the system itself. */
    public static final long SYSTEM_USER_ID = 0L;

    /**
     * Who is making the change: the logged-in member of the current request, or the system when there
     * is none (register and login are anonymous, so their rows are recorded as the system).
     */
    @Bean
    AuditorAware<Long> auditorAware() {
        return new CurrentAuditor();
    }
}
