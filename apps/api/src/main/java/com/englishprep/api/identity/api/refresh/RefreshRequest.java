package com.englishprep.api.identity.api.refresh;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/auth/refresh. A real token has 43 characters; the limit only stops huge inputs. */
public record RefreshRequest(
        @NotBlank @Size(max = 200)
        @Schema(description = "The refresh token received at login or at the previous refresh")
        String refreshToken) {
}
