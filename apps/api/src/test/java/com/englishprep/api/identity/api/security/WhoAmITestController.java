package com.englishprep.api.identity.api.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exists only in the test sources: a protected endpoint to prove the bearer filter works end to end,
 * before the real one (GET /api/me) is built.
 */
@RestController
class WhoAmITestController {

    @GetMapping("/api/test/whoami")
    long whoAmI(@AuthenticationPrincipal AuthenticatedMember member) {
        return member.id();
    }
}
