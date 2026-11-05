package com.englishprep.api.identity.api;

import com.englishprep.api.shared.validation.MaxUtf8Bytes;
import com.englishprep.api.shared.validation.ValidTimeZone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of POST /api/auth/register. */
public record RegisterRequest(
        @NotBlank @Size(max = 100)
        String firstName,

        @NotBlank @Size(max = 100)
        String lastName,

        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "may only contain letters, digits, '_', '.' and '-'")
        String userName,

        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank @Size(min = 8, max = 72) @MaxUtf8Bytes(72)
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        String password,

        @ValidTimeZone
        @Schema(description = "IANA time zone id. Defaults to UTC when omitted.", example = "Asia/Ho_Chi_Minh")
        String timeZone) {

    /** Keeps the raw password out of logs. */
    @Override
    public String toString() {
        return "RegisterRequest[userName=" + userName + ", email=" + email + ", password=***]";
    }
}
