package com.englishprep.api.identity.api;

import com.englishprep.api.identity.application.IssueSessionUseCase;
import com.englishprep.api.identity.application.LoginCommand;
import com.englishprep.api.identity.application.LoginWithPasswordUseCase;
import com.englishprep.api.identity.application.RegisterMemberCommand;
import com.englishprep.api.identity.application.RegisterMemberUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
class AuthController {

    private final RegisterMemberUseCase registerMember;
    private final LoginWithPasswordUseCase loginWithPassword;
    private final IssueSessionUseCase issueSession;

    AuthController(RegisterMemberUseCase registerMember, LoginWithPasswordUseCase loginWithPassword,
                   IssueSessionUseCase issueSession) {
        this.registerMember = registerMember;
        this.loginWithPassword = loginWithPassword;
        this.issueSession = issueSession;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account",
            description = "Creates a member account. It does not log the member in; use the login endpoint for that.")
    RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        var member = registerMember.register(new RegisterMemberCommand(request.firstName(), request.lastName(),
                request.userName(), request.email(), request.password(), request.timeZone()));
        return RegisterResponse.from(member);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in",
            description = "Log in with a user name or email and a password. Returns an access token (short-lived, "
                    + "sent as a Bearer token) and a refresh token (long-lived, shown only this once).")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        var member = loginWithPassword.login(new LoginCommand(request.login(), request.password()));
        var result = issueSession.issue(member);
        // The body holds secrets, so no cache (browser, proxy) may keep it.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(LoginResponse.from(result));
    }
}
