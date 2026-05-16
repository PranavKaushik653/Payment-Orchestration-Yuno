package com.yuno.payments.exception;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public final class GlobalExceptionHandler {

    // =========================================================
    // DTO Validation Errors (@Valid RequestBody)
    // =========================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex
    ) {

        Map<String, String> fieldErrors =
                ex.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .collect(Collectors.toMap(
                                FieldError::getField,
                                error -> error.getDefaultMessage() != null
                                        ? error.getDefaultMessage()
                                        : "Invalid value",
                                (a, b) -> a
                        ));

        ErrorResponse response = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Request validation failed.")
                .details(fieldErrors)
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    // =========================================================
    // Spring Method Validation Errors
    // =========================================================

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerValidation(
            HandlerMethodValidationException ex
    ) {

        Map<String, String> details = new HashMap<>();

        ex.getAllValidationResults().forEach(result -> {

            String fieldName = result.getMethodParameter().getParameterName();

            result.getResolvableErrors().forEach(error -> {

                details.put(
                        fieldName,
                        error.getDefaultMessage() != null
                                ? error.getDefaultMessage()
                                : "Invalid value"
                );
            });
        });

        ErrorResponse response = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Request validation failed.")
                .details(details)
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    // =========================================================
    // Invalid UUID / Path Variable Type Errors
    // =========================================================

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex
    ) {

        Map<String, String> details = new HashMap<>();

        details.put(
                ex.getName(),
                "Invalid value: " + ex.getValue()
        );

        ErrorResponse response = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message("Invalid request parameter.")
                .details(details)
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    // =========================================================
    // Missing Headers
    // =========================================================

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(
            MissingRequestHeaderException ex
    ) {

        Map<String, String> details = new HashMap<>();

        details.put(
                ex.getHeaderName(),
                "Required header is missing"
        );

        ErrorResponse response = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message("Missing required header.")
                .details(details)
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    // =========================================================
    // Business Exceptions
    // =========================================================

    @ExceptionHandler(PaymentExceptions.DuplicateIdempotencyKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(
            PaymentExceptions.DuplicateIdempotencyKeyException ex
    ) {

        log.warn("Idempotency conflict: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        409,
                        "Duplicate Request",
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(PaymentExceptions.PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            PaymentExceptions.PaymentNotFoundException ex
    ) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        404,
                        "Not Found",
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(PaymentExceptions.ProviderUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleProviderUnavailable(
            PaymentExceptions.ProviderUnavailableException ex
    ) {

        log.error("Provider unavailable: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(
                        503,
                        "Provider Unavailable",
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(PaymentExceptions.AllProvidersFailedException.class)
    public ResponseEntity<ErrorResponse> handleAllProvidersFailed(
            PaymentExceptions.AllProvidersFailedException ex
    ) {

        log.error("All providers failed: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of(
                        502,
                        "Payment Processing Failed",
                        ex.getMessage()
                ));
    }

    // =========================================================
    // Generic Fallback
    // =========================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {

        log.error("Unexpected error: ", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        500,
                        "Internal Server Error",
                        "An unexpected error occurred. Please contact support."
                ));
    }

    // =========================================================
    // Error Response DTO
    // =========================================================

    @Getter
    @Builder
    public static class ErrorResponse {

        private int status;
        private String error;
        private String message;
        private Map<String, String> details;
        private Instant timestamp;

        public static ErrorResponse of(
                int status,
                String error,
                String message
        ) {

            return ErrorResponse.builder()
                    .status(status)
                    .error(error)
                    .message(message)
                    .timestamp(Instant.now())
                    .build();
        }
    }
}