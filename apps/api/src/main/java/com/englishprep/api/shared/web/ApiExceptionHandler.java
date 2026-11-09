package com.englishprep.api.shared.web;

import com.englishprep.api.identity.application.EmailAlreadyUsedException;
import com.englishprep.api.identity.application.InvalidCredentialsException;
import com.englishprep.api.identity.application.UserNameAlreadyUsedException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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

    private static ProblemDetail conflict(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
        problem.setTitle("Conflict");
        return problem;
    }
}
