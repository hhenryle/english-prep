package com.englishprep.api.identity.api.security;

import com.englishprep.api.identity.application.token.AccessTokenService;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes the bearer filter available to SecurityConfig as a bean named "bearerTokenFilter", so the
 * shared security code does not need to import anything from identity.
 */
@Configuration(proxyBeanMethods = false)
class IdentitySecurityConfig {

    @Bean
    Filter bearerTokenFilter(AccessTokenService accessTokens) {
        return new BearerTokenFilter(accessTokens);
    }

    /**
     * Spring Boot adds every Filter bean to the servlet container by itself. This filter must only run
     * inside the Spring Security chain (where SecurityConfig puts it), so that registration is disabled.
     */
    @Bean
    FilterRegistrationBean<Filter> bearerTokenFilterRegistration(Filter bearerTokenFilter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(bearerTokenFilter);
        registration.setEnabled(false);
        return registration;
    }
}
