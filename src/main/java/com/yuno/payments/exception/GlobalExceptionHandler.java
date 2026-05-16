 package com.yuno.payments.exception;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        f -> f.getDefaultMessage() != null ? f.getDefaultMessage() : "Invalid value",
                        (a, b) -> a  // Keep first error if multiple violations on same field
                ));

        return ResponseEntity.badRequest().body(
                ErrorResponse.builder()
                        .status(400)
                        .error("Validation Failed")
                        .details(fieldErrors)
                        .timestamp(Instant.now())
                        .build()
        );
    }

    @ExceptionHandler(PaymentExceptions.DuplicateIdempotencyKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(PaymentExceptions.DuplicateIdempotencyKeyException ex) {
        log.warn("Idempotency conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Duplicate Request", ex.getMessage()));
    }

    @ExceptionHandler(PaymentExceptions.PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(PaymentExceptions.PaymentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Not Found", ex.getMessage()));
    }

    @ExceptionHandler(PaymentExceptions.AllProvidersFailedException.class)
    public ResponseEntity<ErrorResponse> handleAllProvidersFailed(PaymentExceptions.AllProvidersFailedException ex) {
        log.error("All providers failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of(502, "Payment Processing Failed", ex.getMessage()));
    }

    @ExceptionHandler(PaymentExceptions.ProviderUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleProviderUnavailable(PaymentExceptions.ProviderUnavailableException ex) {
        log.error("Provider unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(503, "Provider Unavailable", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        // Log full stack trace internally but NEVER expose it to the client
        log.error("Unexpected error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "Internal Server Error",
                        "An unexpected error occurred. Please contact support."));
    }

    @Getter
    @Builder
    public static class ErrorResponse {
        private int status;
        private String error;
        private String message;
        private Map<String, String> details;
        private Instant timestamp;

        public static ErrorResponse of(int status, String error, String message) {
            return ErrorResponse.builder()
                    .status(status)
                    .error(error)
                    .message(message)
                    .timestamp(Instant.now())
                    .build();
        }
    }
}
