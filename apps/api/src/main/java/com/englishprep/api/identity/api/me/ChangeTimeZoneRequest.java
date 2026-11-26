package com.englishprep.api.identity.api.me;

import com.englishprep.api.shared.validation.ValidTimeZone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Body of PUT /api/me/time-zone. {@code @ValidTimeZone} accepts null (registration has a default), so
 * {@code @NotBlank} is needed here: a change must name a zone.
 */
public record ChangeTimeZoneRequest(
        @NotBlank @ValidTimeZone
        @Schema(description = "IANA time zone id", example = "Asia/Ho_Chi_Minh")
        String timeZone) {
}
