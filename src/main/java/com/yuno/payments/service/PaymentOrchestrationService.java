package com.yuno.payments.service;

import com.yuno.payments.dto.CreatePaymentRequest;
import com.yuno.payments.dto.PaymentResponse;
import com.yuno.payments.dto.ProviderResult;
import com.yuno.payments.enums.PaymentStatus;
import com.yuno.payments.exception.PaymentExceptions;
import com.yuno.payments.idempotency.IdempotencyStore;
import com.yuno.payments.model.Payment;
import com.yuno.payments.provider.ProviderConnectors.ProviderConnector;
import com.yuno.payments.repository.PaymentRepository;
import com.yuno.payments.routing.RoutingEngine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOrchestrationService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyStore idempotencyStore;
    private final RoutingEngine routingEngine;
    private final MeterRegistry meterRegistry;

    @Transactional
    public PaymentResponse createPayment(
            CreatePaymentRequest request,
            String idempotencyKey) {

        var existingPaymentId = idempotencyStore.findExistingPaymentId(idempotencyKey);
        if (existingPaymentId.isPresent()) {
            log.info("Duplicate request detected for idempotency key={}", idempotencyKey);
            return paymentRepository.findById(UUID.fromString(existingPaymentId.get()))
                    .map(payment -> {
                        PaymentResponse response = PaymentResponse.from(payment);
                        response.setStatus(PaymentStatus.DUPLICATE);
                        response.setMessage("Duplicate request — returning original payment result.");
                        return response;
                    })
                    .orElseThrow(() -> new PaymentExceptions.PaymentNotFoundException(existingPaymentId.get()));
        }

        Payment payment = Payment.builder()
                .idempotencyKey(idempotencyKey)
                .merchantId(request.getMerchantId())
                .amount(request.getAmount())
                .currency(request.getCurrency().toUpperCase())
                .paymentMethod(request.getPaymentMethod())
                .status(PaymentStatus.PENDING)
                .build();

        payment = paymentRepository.save(payment);
        log.info("Payment created id={} method={} status=PENDING", payment.getId(), payment.getPaymentMethod());


        payment.setStatus(PaymentStatus.PROCESSING);
        payment = paymentRepository.save(payment);

        // Use a Micrometer timer to capture end-to-end provider latency
        Timer.Sample timerSample = Timer.start(meterRegistry);
        ProviderResult result;

        try {
            result = processWithFailover(payment);
        } finally {
            // Always record the timer, even on exception
            timerSample.stop(Timer.builder("payment.processing.duration")
                    .tag("method", payment.getPaymentMethod().name())
                    .register(meterRegistry));
        }

        // ── STEP 4: UPDATE FINAL STATUS ───────────────────────────────────────
        if (result.isSuccess()) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setProviderTransactionId(result.getProviderTransactionId());
            meterRegistry.counter("payment.success", "method", payment.getPaymentMethod().name()).increment();
            log.info("Payment SUCCESS id={} provider={} txnId={}",
                    payment.getId(), payment.getAssignedProvider(), result.getProviderTransactionId());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(result.getErrorMessage());
            meterRegistry.counter("payment.failure", "method", payment.getPaymentMethod().name()).increment();
            log.error("Payment FAILED id={} reason={}", payment.getId(), result.getErrorMessage());
        }

        payment = paymentRepository.save(payment);
        idempotencyStore.store(idempotencyKey, payment.getId().toString());

        return PaymentResponse.from(payment);
    }

    /**
     * Fetches an existing payment by its UUID.
     */
    @Transactional(readOnly = true)  // readOnly hint lets JPA skip dirty-checking
    public PaymentResponse getPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentExceptions.PaymentNotFoundException(paymentId.toString()));
    }

    private ProviderResult processWithFailover(Payment payment) {
        // Resolve primary connector based on payment method
        ProviderConnector primary = routingEngine.resolve(payment.getPaymentMethod());
        payment.setAssignedProvider(primary.getProvider());

        try {
            log.debug("Attempting primary provider={} for payment={}", primary.getProvider(), payment.getId());
            return primary.process(payment);

        } catch (PaymentExceptions.ProviderUnavailableException primaryEx) {
            log.warn("Primary provider {} failed for payment={}, attempting failover",
                    primary.getProvider(), payment.getId());

            // Try the failover provider
            ProviderConnector failover = routingEngine.resolveFailover(primary.getProvider());
            payment.setAssignedProvider(failover.getProvider());

            try {
                return failover.process(payment);
            } catch (PaymentExceptions.ProviderUnavailableException failoverEx) {
                log.error("Failover provider {} also failed for payment={}",
                        failover.getProvider(), payment.getId());
                throw new PaymentExceptions.AllProvidersFailedException();
            }
        }
    }
}