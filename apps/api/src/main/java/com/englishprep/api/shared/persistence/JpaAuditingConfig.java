package com.englishprep.api.shared.persistence;

import java.util.Optional;
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
     * Who is making the change. Always the system for now; once login exists (step 2.5)
     * this will return the id of the authenticated person.
     */
    @Bean
    AuditorAware<Long> auditorAware() {
        return () -> Optional.of(SYSTEM_USER_ID);
    }
}
