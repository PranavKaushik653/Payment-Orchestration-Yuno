package com.yuno.payments.dto;

import com.yuno.payments.enums.PaymentMethod;
import com.yuno.payments.enums.PaymentStatus;
import com.yuno.payments.enums.Provider;
import com.yuno.payments.model.Payment;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder(toBuilder = true)
public class PaymentResponse {
    private UUID paymentId;
    private String idempotencyKey;
    private String merchantId;
    private BigDecimal amount;
    private String currency;
    private PaymentMethod paymentMethod;
    private PaymentStatus status;
    private Provider assignedProvider;
    private String providerTransactionId;
    private String message;
    private Instant createdAt;
    private Instant updatedAt;

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .idempotencyKey(payment.getIdempotencyKey())
                .merchantId(payment.getMerchantId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus())
                .assignedProvider(payment.getAssignedProvider())
                .providerTransactionId(payment.getProviderTransactionId())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
