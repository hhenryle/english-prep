package com.englishprep.api.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MemberTest {

    private static Member newMember() {
        return Member.register(" Alice ", "Nguyen", "  Alice01 ", "  Alice@Example.COM ", "hash",
                "Asia/Ho_Chi_Minh");
    }

    @Test
    void registerNormalizesLoginFieldsAndLeavesDatabaseFieldsEmpty() {
        Member member = newMember();

        assertThat(member.firstName()).isEqualTo("Alice");
        assertThat(member.lastName()).isEqualTo("Nguyen");
        assertThat(member.userName()).isEqualTo("alice01");
        assertThat(member.email()).isEqualTo("alice@example.com");
        assertThat(member.passwordHash()).isEqualTo("hash");
        assertThat(member.timeZone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(member.id()).isNull();
        assertThat(member.createdAt()).isNull();
        assertThat(member.updatedAt()).isNull();
    }

    @Test
    void userIsAPerson() {
        assertThat(newMember()).isInstanceOf(Person.class);
    }

    @Test
    void registerRejectsBlankFields() {
        assertThatThrownBy(() -> Member.register(" ", "N", "u", "a@x.com", "h", "UTC"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("firstName");
        assertThatThrownBy(() -> Member.register("A", "", "u", "a@x.com", "h", "UTC"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lastName");
        assertThatThrownBy(() -> Member.register("A", "N", " ", "a@x.com", "h", "UTC"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("userName");
        assertThatThrownBy(() -> Member.register("A", "N", "u", null, "h", "UTC"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("email");
        assertThatThrownBy(() -> Member.register("A", "N", "u", "a@x.com", "", "UTC"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("passwordHash");
        assertThatThrownBy(() -> Member.register("A", "N", "u", "a@x.com", "h", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("timeZone");
    }

    @Test
    void registerRejectsUnknownTimeZone() {
        assertThatThrownBy(() -> Member.register("A", "N", "u", "a@x.com", "h", "Mars/Olympus"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("timeZone");
    }
}
