package com.yuno.payments.controller;

import com.yuno.payments.dto.*;
import com.yuno.payments.service.PaymentOrchestrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";
    private final PaymentOrchestrationService orchestrationService;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader(IDEMPOTENCY_KEY_HEADER)
            @NotBlank(message = "X-Idempotency-Key header must not be blank")
            String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {

        log.info("Received create payment request: merchantId={} method={} idempotencyKey={}",
                request.getMerchantId(), request.getPaymentMethod(), idempotencyKey);

        PaymentResponse response = orchestrationService.createPayment(request, idempotencyKey);
        HttpStatus status = response.getStatus() == com.yuno.payments.enums.PaymentStatus.DUPLICATE
                ? HttpStatus.OK
                : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentId) {
        log.info("Fetching payment id={}", paymentId);
        return ResponseEntity.ok(orchestrationService.getPayment(paymentId));
    }
}