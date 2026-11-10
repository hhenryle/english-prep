package com.englishprep.api.shared.security;

import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Web security rules: who may call what. A valid bearer token (see BearerTokenFilter) identifies the caller; anonymous callers get a 401 on every protected path.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    /** POST endpoints anyone may call, because the caller has no token yet. */
    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/api/auth/register", "/api/auth/login", "/api/auth/refresh"};

    /** API documentation, used while developing. Disable it in production (see springdoc.* properties). */
    private static final String[] API_DOCS = {"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
            @Qualifier("bearerTokenFilter") Filter bearerTokenFilter) throws Exception {
        http
                // CSRF protects cookie-based sessions. We use a bearer token in a header, not cookies.
                .csrf(AbstractHttpConfigurer::disable)
                // No server-side session: every request must carry its own token.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Spring Security's default login page, Basic prompt and logout page are not used by an API.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // Reads the bearer token and, when it is valid, records who the caller is. It runs before the
                // access rules below are checked.
                .addFilterBefore(bearerTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .requestMatchers(API_DOCS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                // Without this, an anonymous call to a protected path would answer 403 instead of 401.
                // The 401 body is built by ApiExceptionHandler, like every other error.
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint(exceptionResolver)));
        return http.build();
    }
}
