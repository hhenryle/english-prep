package com.englishprep.api.identity.api.me;

import com.englishprep.api.identity.api.login.LoginResponse;
import com.englishprep.api.identity.application.me.GetCurrentMemberUseCase;
import com.englishprep.api.identity.application.password.ChangePasswordUseCase;
import com.englishprep.api.shared.security.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Account")
class MeController {

    private final GetCurrentMemberUseCase getCurrentMember;
    private final ChangePasswordUseCase changePassword;

    MeController(GetCurrentMemberUseCase getCurrentMember, ChangePasswordUseCase changePassword) {
        this.getCurrentMember = getCurrentMember;
        this.changePassword = changePassword;
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

    @PutMapping("/password")
    @Operation(summary = "Change password",
            description = "Needs the current password. Every refresh token of the member is revoked, so other "
                    + "devices must log in again. The response holds a new session (same format as login) for "
                    + "the device that made the request.")
    ResponseEntity<LoginResponse> changePassword(@AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody ChangePasswordRequest request) {
        var result = changePassword.change(member.id(), request.currentPassword(), request.newPassword());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(LoginResponse.from(result));
    }
}
