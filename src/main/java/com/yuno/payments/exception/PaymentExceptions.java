package com.yuno.payments.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;


public final class PaymentExceptions {

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class DuplicateIdempotencyKeyException extends RuntimeException {
        public DuplicateIdempotencyKeyException(String key) {
            super("Payment with idempotency key '" + key + "' already processed.");
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException(String paymentId) {
            super("Payment not found: " + paymentId);
        }
    }

    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public static class ProviderUnavailableException extends RuntimeException {
        public ProviderUnavailableException(String provider, String reason) {
            super("Provider " + provider + " unavailable: " + reason);
        }
    }

    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public static class AllProvidersFailedException extends RuntimeException {
        public AllProvidersFailedException() {
            super("All payment providers failed. Please retry later.");
        }
    }

    private PaymentExceptions() {}
}