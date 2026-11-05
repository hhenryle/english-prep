package com.englishprep.api.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserTest {

    private static User newUser() {
        return User.register(" Alice ", "Nguyen", "  Alice01 ", "  Alice@Example.COM ", "hash",
                "Asia/Ho_Chi_Minh");
    }

    @Test
    void registerNormalizesLoginFieldsAndLeavesDatabaseFieldsEmpty() {
        User user = newUser();

        assertThat(user.firstName()).isEqualTo("Alice");
        assertThat(user.lastName()).isEqualTo("Nguyen");
        assertThat(user.userName()).isEqualTo("alice01");
        assertThat(user.email()).isEqualTo("alice@example.com");
        assertThat(user.passwordHash()).isEqualTo("hash");
        assertThat(user.timeZone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(user.id()).isNull();
        assertThat(user.createdAt()).isNull();
        assertThat(user.updatedAt()).isNull();
    }

    @Test
    void userIsAPerson() {
        assertThat(newUser()).isInstanceOf(Person.class);
    }

    @Test
    void registerRejectsBlankFields() {
        assertThatThrownBy(() -> User.register(" ", "N", "u", "a@x.com", "h", "UTC"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("firstName");
        assertThatThrownBy(() -> User.register("A", "", "u", "a@x.com", "h", "UTC"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lastName");
        assertThatThrownBy(() -> User.register("A", "N", " ", "a@x.com", "h", "UTC"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("userName");
        assertThatThrownBy(() -> User.register("A", "N", "u", null, "h", "UTC"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("email");
        assertThatThrownBy(() -> User.register("A", "N", "u", "a@x.com", "", "UTC"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("passwordHash");
        assertThatThrownBy(() -> User.register("A", "N", "u", "a@x.com", "h", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("timeZone");
    }

    @Test
    void registerRejectsUnknownTimeZone() {
        assertThatThrownBy(() -> User.register("A", "N", "u", "a@x.com", "h", "Mars/Olympus"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("timeZone");
    }
}
