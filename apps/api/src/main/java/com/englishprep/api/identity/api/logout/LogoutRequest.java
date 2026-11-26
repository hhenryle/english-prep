package com.englishprep.api.identity.api.logout;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/auth/logout. */
public record LogoutRequest(
        @NotBlank @Size(max = 200)
        @Schema(description = "The refresh token of the session to end")
        String refreshToken) {
}
