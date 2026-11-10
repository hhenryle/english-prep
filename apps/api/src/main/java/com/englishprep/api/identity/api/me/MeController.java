package com.englishprep.api.identity.api.me;

import com.englishprep.api.identity.api.security.AuthenticatedMember;
import com.englishprep.api.identity.application.me.GetCurrentMemberUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Account")
class MeController {

    private final GetCurrentMemberUseCase getCurrentMember;

    MeController(GetCurrentMemberUseCase getCurrentMember) {
        this.getCurrentMember = getCurrentMember;
    }

    /**
     * The first endpoint that needs a login. {@code member} is filled in by BearerTokenFilter from the
     * access token; Spring Security has already answered 401 before this method runs when it is missing.
     */
    @GetMapping
    @Operation(summary = "Current member",
            description = "The account of the caller. Needs an access token: Authorization: Bearer <token>.")
    ResponseEntity<MeResponse> me(@AuthenticationPrincipal AuthenticatedMember member) {
        var current = getCurrentMember.get(member.id());
        // Personal data: no cache (browser, proxy) may keep it.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(MeResponse.from(current));
    }
}
