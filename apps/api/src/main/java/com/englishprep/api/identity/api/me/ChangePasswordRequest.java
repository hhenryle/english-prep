package com.englishprep.api.identity.api.me;

import com.englishprep.api.shared.validation.MaxUtf8Bytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of PUT /api/me/password. The current password is only checked for presence and length (like
 * login); the new one follows the same rules as registration.
 */
public record ChangePasswordRequest(
        @NotBlank @Size(max = 72)
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        String currentPassword,

        @NotBlank @Size(min = 8, max = 72) @MaxUtf8Bytes(72)
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        String newPassword) {

    /** Keeps both passwords out of logs. */
    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=***, newPassword=***]";
    }
}
