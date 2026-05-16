package com.yuno.payments.dto;

import com.yuno.payments.enums.PaymentMethod;
import com.yuno.payments.model.Payment;
import lombok.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePaymentRequest {

    @NotBlank(message = "merchantId must not be blank")
    @Size(max = 64, message = "merchantId must be at most 64 characters")
    private String merchantId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    @Digits(integer = 15, fraction = 4, message = "amount format invalid")
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code (e.g. USD)")
    private String currency;

    @NotNull(message = "paymentMethod is required (CARD or UPI)")
    private PaymentMethod paymentMethod;
}

