package com.englishprep.api.shared.web;

import com.englishprep.api.identity.application.login.InvalidCredentialsException;
import com.englishprep.api.identity.application.me.CurrentMemberNotFoundException;
import com.englishprep.api.identity.application.password.PasswordNotChangedException;
import com.englishprep.api.identity.application.refresh.InvalidRefreshTokenException;
import com.englishprep.api.identity.application.register.EmailAlreadyUsedException;
import com.englishprep.api.identity.application.register.UserNameAlreadyUsedException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 9457 "problem details" JSON. The parent class already handles the
 * standard Spring MVC errors (bad JSON, wrong method, ...); this adds our own cases.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** 400 with the list of invalid fields: {"errors": {"email": "must be a well-formed email address"}}. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Invalid request");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler({EmailAlreadyUsedException.class, UserNameAlreadyUsedException.class})
    ProblemDetail handleAccountTaken(RuntimeException ex) {
        return conflict(ex.getMessage());
    }

    /**
     * Two requests can both pass the "already used?" check and one then hits the unique index
     * in the database. That is the same situation, so it is also a 409.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrityViolation(DataIntegrityViolationException ex) {
        return conflict("Email or user name is already registered");
    }

    /** 401 for a failed login. The message is the same for every reason, so it reveals nothing about accounts. */
    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Unauthorized");
        return problem;
    }

    /** 400 when the new password equals the current one. */
    @ExceptionHandler(PasswordNotChangedException.class)
    ProblemDetail handlePasswordNotChanged(PasswordNotChangedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }

    /** 401 for a refresh token that cannot be used. Same message for every reason. */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Unauthorized");
        return problem;
    }

    /**
     * 401 for a protected path called without a valid token. One message for every reason (no token,
     * bad token, expired token, member no longer exists), and the header tells the client that a Bearer
     * token is expected.
     */
    @ExceptionHandler({AuthenticationException.class, CurrentMemberNotFoundException.class})
    ResponseEntity<ProblemDetail> handleAuthentication(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication is required");
        problem.setTitle("Unauthorized");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(problem);
    }

    /**
     * 403: the caller is logged in but not allowed to do this. Nothing needs it yet; it is here so the
     * format is already right when roles exist (it also covers AccessDeniedException thrown by method security).
     */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "You are not allowed to do this");
        problem.setTitle("Forbidden");
        return problem;
    }

    /**
     * 500 for anything not handled above. The client gets a generic message and a reference; the real
     * exception goes only to the log, under the same reference, so it can be found when someone reports it.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        String reference = UUID.randomUUID().toString();
        log.error("Unexpected error, reference {}", reference, ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
        problem.setTitle("Internal Server Error");
        problem.setProperty("reference", reference);
        return problem;
    }

    private static ProblemDetail conflict(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
        problem.setTitle("Conflict");
        return problem;
    }
}
