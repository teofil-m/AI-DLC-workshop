package com.ai_dlc.workshop.web;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;

/**
 * Global exception handler that converts exceptions to RFC 9457 ProblemDetail responses.
 * No internal stack traces or implementation details are exposed to callers.
 */
@RestControllerAdvice
@Slf4j
public class ProblemDetailControllerAdvice {

    /**
     * Handles {@link ResponseStatusException} thrown from service or controller layer.
     * The HTTP status and reason from the exception are reflected in the ProblemDetail.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatusException(ResponseStatusException ex) {
        log.debug("ResponseStatusException: status={} reason={}", ex.getStatusCode(), ex.getReason());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        return ResponseEntity.of(problem).build();
    }

    /**
     * Catch-all handler for unexpected exceptions.
     * Returns a generic 500 response — no implementation details are leaked.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
        return ResponseEntity.of(problem).build();
    }
}
