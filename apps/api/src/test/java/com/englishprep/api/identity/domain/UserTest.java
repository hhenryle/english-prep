package com.englishprep.api.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void registerNormalizesEmailAndGeneratesIdAndTimestamps() {
        User user = User.register("  Alice@Example.COM ", "hash", "Asia/Ho_Chi_Minh");

        assertThat(user.id()).isNotNull();
        assertThat(user.email()).isEqualTo("alice@example.com");
        assertThat(user.passwordHash()).isEqualTo("hash");
        assertThat(user.timeZone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(user.createdAt()).isNotNull().isEqualTo(user.updatedAt());
    }

    @Test
    void registerGeneratesDifferentIds() {
        User first = User.register("a@x.com", "hash", "UTC");
        User second = User.register("b@x.com", "hash", "UTC");

        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    void registerRejectsBlankFields() {
        assertThatThrownBy(() -> User.register(" ", "hash", "UTC"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("email");
        assertThatThrownBy(() -> User.register("a@x.com", "", "UTC"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("passwordHash");
        assertThatThrownBy(() -> User.register("a@x.com", "hash", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("timeZone");
    }
}
