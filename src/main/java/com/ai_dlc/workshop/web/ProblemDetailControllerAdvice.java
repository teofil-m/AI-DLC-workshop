package com.ai_dlc.workshop.web;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;

/**
 * Global exception handler returning RFC 9457 ProblemDetail responses.
 * Internal detail is only forwarded for 4xx client errors — 5xx responses
 * always return a generic message to prevent information disclosure.
 */
@RestControllerAdvice
@Slf4j
public class ProblemDetailControllerAdvice {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Bean Validation failed: {}", detail);
        return ResponseEntity.of(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail)).build();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        if (status.is4xxClientError()) {
            log.warn("Client error: status={} reason={}", status, ex.getReason());
            String detail = ex.getReason() != null ? ex.getReason() : status.toString();
            return ResponseEntity.of(ProblemDetail.forStatusAndDetail(status, detail)).build();
        }
        // 5xx: log the real cause internally, return generic message to caller
        log.error("Server error wrapped in ResponseStatusException: status={}", status, ex);
        return ResponseEntity.of(ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred")).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.of(ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred")).build();
    }
}
