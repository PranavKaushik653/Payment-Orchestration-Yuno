package com.yuno.payments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ProviderResult {

    private boolean success;
    private String providerTransactionId;
    private String errorMessage;

    public static ProviderResult success(String txnId) {

        return ProviderResult.builder()
                .success(true)
                .providerTransactionId(txnId)
                .build();
    }

    public static ProviderResult failure(String reason) {
            return ProviderResult.builder()
                    .success(false)
                    .errorMessage(reason)
                    .build();
    }
}
