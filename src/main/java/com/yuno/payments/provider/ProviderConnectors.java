package com.yuno.payments.provider;

import com.yuno.payments.enums.Provider;
import com.yuno.payments.exception.PaymentExceptions;
import com.yuno.payments.model.Payment;
import com.yuno.payments.dto.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

public final class ProviderConnectors {

    // ─── Interface ────────────────────────────────────────────────────────────

    /**
     * Common contract all payment provider connectors must implement.
     * Using an interface makes mocking trivial in tests:
     * <pre>
     *   ProviderConnector mockProvider = Mockito.mock(ProviderConnector.class);
     *   when(mockProvider.process(any())).thenReturn(ProviderResult.success("TXN-123"));
     * </pre>
     */
    public interface ProviderConnector {
        ProviderResult process(Payment payment);
        Provider getProvider();
    }
    @Component
    @Slf4j
    public static class ProviderAConnector implements ProviderConnector {

        @Override
        @Retry(name = "providerA")
        @CircuitBreaker(name = "providerA", fallbackMethod = "fallback")
        public ProviderResult process(Payment payment) {
            log.info("Provider A processing payment id={} amount={} {}",
                    payment.getId(), payment.getAmount(), payment.getCurrency());

            String externalTxnId = "PA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            log.info("Provider A SUCCESS: externalTxnId={}", externalTxnId);
            return ProviderResult.success(externalTxnId);
        }

        @Override
        public Provider getProvider() {
            return Provider.PROVIDER_A;
        }

        public ProviderResult fallback(Payment payment, Throwable t) {
            log.error("Provider A circuit OPEN or retries exhausted for payment={}: {}",
                    payment.getId(), t.getMessage());
            throw new PaymentExceptions.ProviderUnavailableException("PROVIDER_A", t.getMessage());
        }
    }

    @Component
    @Slf4j
    public static class ProviderBConnector implements ProviderConnector {

        @Override
        @Retry(name = "providerB")
        @CircuitBreaker(name = "providerB", fallbackMethod = "fallback")
        public ProviderResult process(Payment payment) {
            log.info("Provider B processing payment id={} amount={} {}",
                    payment.getId(), payment.getAmount(), payment.getCurrency());

            // ── STUB: Replace with actual UPI PSP HTTP client call ──
            String externalTxnId = "PB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            log.info("Provider B SUCCESS: externalTxnId={}", externalTxnId);
            return ProviderResult.success(externalTxnId);
        }

        @Override
        public Provider getProvider() {
            return Provider.PROVIDER_B;
        }

        public ProviderResult fallback(Payment payment, Throwable t) {
            log.error("Provider B circuit OPEN or retries exhausted for payment={}: {}",
                    payment.getId(), t.getMessage());
            throw new PaymentExceptions.ProviderUnavailableException("PROVIDER_B", t.getMessage());
        }
    }

    private ProviderConnectors() {}
}