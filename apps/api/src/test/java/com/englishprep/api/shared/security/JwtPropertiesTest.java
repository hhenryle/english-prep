package com.englishprep.api.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Starts only the JWT configuration (no web, no database) with different property values. */
class JwtPropertiesTest {

    private static final String SECRET = "x".repeat(32);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(JwtConfig.class);

    @Test
    void usesDefaultsForEverythingExceptTheSecret() {
        runner.withPropertyValues("app.jwt.secret=" + SECRET).run(context -> {
            JwtProperties properties = context.getBean(JwtProperties.class);

            assertThat(properties.secret()).isEqualTo(SECRET);
            assertThat(properties.issuer()).isEqualTo("english-prep");
            assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.refreshTokenTtl()).isEqualTo(Duration.ofDays(7));
        });
    }

    @Test
    void readsConfiguredValues() {
        runner.withPropertyValues("app.jwt.secret=" + SECRET, "app.jwt.issuer=other",
                "app.jwt.access-token-ttl=5m", "app.jwt.refresh-token-ttl=30d").run(context -> {
            JwtProperties properties = context.getBean(JwtProperties.class);

            assertThat(properties.issuer()).isEqualTo("other");
            assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.refreshTokenTtl()).isEqualTo(Duration.ofDays(30));
        });
    }

    @Test
    void failsToStartWhenTheSecretIsMissing() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessage(context.getStartupFailure())).contains("JWT_SECRET");
        });
    }

    @Test
    void failsToStartWhenTheSecretIsShorterThan32Bytes() {
        runner.withPropertyValues("app.jwt.secret=" + "x".repeat(31)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessage(context.getStartupFailure())).contains("at least 32 bytes");
        });
    }

    @Test
    void failsToStartWhenALifetimeIsNotPositive() {
        runner.withPropertyValues("app.jwt.secret=" + SECRET, "app.jwt.access-token-ttl=0s").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessage(context.getStartupFailure())).contains("access-token-ttl");
        });
    }

    @Test
    void toStringDoesNotShowTheSecret() {
        runner.withPropertyValues("app.jwt.secret=" + SECRET).run(context ->
                assertThat(context.getBean(JwtProperties.class).toString()).doesNotContain(SECRET));
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
