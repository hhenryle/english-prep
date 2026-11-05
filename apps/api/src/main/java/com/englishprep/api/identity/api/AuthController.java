package com.englishprep.api.identity.api;

import com.englishprep.api.identity.application.RegisterMemberCommand;
import com.englishprep.api.identity.application.RegisterMemberUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    AuthController(RegisterMemberUseCase registerMember) {
        this.registerMember = registerMember;
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
}
