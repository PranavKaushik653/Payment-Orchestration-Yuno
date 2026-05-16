package com.yuno.payments.model;

import com.yuno.payments.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "payments",
        indexes = {
                // Idempotency key lookups must be fast — indexed
                @Index(name = "idx_payments_idempotency_key", columnList = "idempotency_key"),
                // Common query pattern: "show all payments for merchant X"
                @Index(name = "idx_payments_merchant_id", columnList = "merchant_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "idempotency_key", unique = true, nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "merchant_id", nullable = false, length = 64)
    private String merchantId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_provider", length = 20)
    private Provider assignedProvider;

    @Column(name = "provider_transaction_id", length = 128)
    private String providerTransactionId;

    /** Raw error message if the payment failed — kept for debugging/audit. */
    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

}