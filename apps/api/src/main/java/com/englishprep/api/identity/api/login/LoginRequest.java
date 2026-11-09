package com.englishprep.api.identity.api.login;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/auth/login. Only presence and length are checked here, not the password
 * rules of registration, so the answer never reveals what a valid password looks like.
 */
public record LoginRequest(
        @NotBlank @Size(max = 255)
        @Schema(description = "User name or email")
        String login,

        @NotBlank @Size(max = 72)
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        String password) {
}
