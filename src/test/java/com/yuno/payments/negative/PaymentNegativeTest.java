package com.yuno.payments.negative;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuno.payments.enums.*;
import com.yuno.payments.exception.PaymentExceptions;
import com.yuno.payments.idempotency.IdempotencyStore;
import com.yuno.payments.model.Payment;
import com.yuno.payments.dto.*;
import com.yuno.payments.provider.ProviderConnectors.ProviderConnector;
import com.yuno.payments.routing.RoutingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Negative Tests: Failure Modes & Error Handling")
class PaymentNegativeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RoutingEngine routingEngine;

    @Autowired
    private IdempotencyStore idempotencyStore;

    private ProviderConnector mockConnector;

    private static final String API_PATH = "/api/v1/payments";
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    @BeforeEach
    void setUp() {
        mockConnector = mock(ProviderConnector.class);
        when(mockConnector.getProvider()).thenReturn(Provider.PROVIDER_A);
        when(routingEngine.resolve(any())).thenReturn(mockConnector);
        when(routingEngine.getProvider(any())).thenReturn(Provider.PROVIDER_A);
    }

    // ── TC-NEG-001: Missing Required Fields ──────────────────────────────────

    @Test
    @DisplayName("TC-NEG-001: Missing merchantId returns 400 Bad Request")
    void createPayment_missingMerchantId_returns400() throws Exception {

        String requestJson = """
                {
                    "amount": 100.00,
                    "currency": "USD",
                    "paymentMethod": "CARD"
                }
                """;

        mockMvc.perform(
                        post(API_PATH)
                                .header(IDEMPOTENCY_HEADER, UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest())              // 400
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    if (!body.contains("merchantId must not be blank")) {
                        throw new AssertionError(
                                "Expected 'merchantId must not be blank' in response but got: " + body);
                    }
                });
    }
    @Test
    @DisplayName("TC-NEG-002: Zero amount returns 400 Bad Request")
    void createPayment_zeroAmount_returns400() throws Exception {

        String requestJson = """
                {
                    "merchantId": "merchant-001",
                    "amount": 0.00,
                    "currency": "USD",
                    "paymentMethod": "CARD"
                }
                """;

        mockMvc.perform(
                        post(API_PATH)
                                .header(IDEMPOTENCY_HEADER, UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    if (!body.contains("amount must be greater than zero")) {
                        throw new AssertionError(
                                "Expected 'amount must be greater than zero' in response but got: " + body);
                    }
                });
    }


    @Test
    @DisplayName("TC-NEG-003: Missing X-Idempotency-Key header returns 400")
    void createPayment_missingIdempotencyKey_returns400() throws Exception {

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .merchantId("merchant-001")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .paymentMethod(PaymentMethod.CARD)
                .build();

        mockMvc.perform(
                        post(API_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest()); // Spring returns 400 for missing required headers
    }

    @Test
    @DisplayName("TC-NEG-004: Duplicate idempotency key returns the original payment (not a new charge)")
    void createPayment_duplicateIdempotencyKey_returnsCachedResult() throws Exception {

        when(mockConnector.process(any())).thenReturn(ProviderResult.success("PA-TXN-DUP"));

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .merchantId("merchant-dup")
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .paymentMethod(PaymentMethod.CARD)
                .build();

        String idempotencyKey = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(
                        post(API_PATH)
                                .header(IDEMPOTENCY_HEADER, idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isCreated());

        // Second request — same idempotency key
        mockMvc.perform(
                        post(API_PATH)
                                .header(IDEMPOTENCY_HEADER, idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().is2xxSuccessful()); // Should succeed (not fail)

        verify(mockConnector, times(1)).process(any(Payment.class));
    }


    @Test
    @DisplayName("TC-NEG-005: Primary provider fails, failover succeeds — payment completes")
    void createPayment_primaryProviderDown_failoverSucceeds() throws Exception {

        ProviderConnector failoverConnector = mock(ProviderConnector.class);
        when(failoverConnector.getProvider()).thenReturn(Provider.PROVIDER_B);
        when(failoverConnector.process(any())).thenReturn(ProviderResult.success("PB-FAILOVER-001"));

        when(mockConnector.process(any()))
                .thenThrow(new PaymentExceptions.ProviderUnavailableException("PROVIDER_A", "Connection timeout"));

        when(routingEngine.resolveFailover(Provider.PROVIDER_A)).thenReturn(failoverConnector);

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .merchantId("merchant-failover")
                .amount(new BigDecimal("300.00"))
                .currency("USD")
                .paymentMethod(PaymentMethod.CARD)
                .build();

        mockMvc.perform(
                        post(API_PATH)
                                .header(IDEMPOTENCY_HEADER, UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.providerTransactionId").value("PB-FAILOVER-001"));

        verify(mockConnector, times(1)).process(any());
        verify(failoverConnector, times(1)).process(any());
    }

    @Test
    @DisplayName("TC-NEG-006: Both providers fail — returns 502 Bad Gateway")
    void createPayment_allProvidersFail_returns502() throws Exception {

        ProviderConnector failoverConnector = mock(ProviderConnector.class);
        when(failoverConnector.getProvider()).thenReturn(Provider.PROVIDER_B);

        // Both providers throw exceptions
        when(mockConnector.process(any()))
                .thenThrow(new PaymentExceptions.ProviderUnavailableException("PROVIDER_A", "Down"));
        when(failoverConnector.process(any()))
                .thenThrow(new PaymentExceptions.ProviderUnavailableException("PROVIDER_B", "Also down"));

        when(routingEngine.resolveFailover(any())).thenReturn(failoverConnector);

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .merchantId("merchant-alldown")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .paymentMethod(PaymentMethod.CARD)
                .build();

        mockMvc.perform(
                        post(API_PATH)
                                .header(IDEMPOTENCY_HEADER, UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadGateway())          // 502
                .andExpect(jsonPath("$.error").value("Payment Processing Failed"));
    }


    @Test
    @DisplayName("TC-NEG-007: Fetching non-existent payment returns 404")
    void getPayment_notFound_returns404() throws Exception {

        UUID nonExistentId = UUID.randomUUID(); // Random UUID that's not in the DB

        mockMvc.perform(get(API_PATH + "/" + nonExistentId))
                .andExpect(status().isNotFound())            // 404
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    // ── TC-NEG-008: Invalid Currency Format ──────────────────────────────────

    @Test
    @DisplayName("TC-NEG-008: Currency longer than 3 chars returns 400")
    void createPayment_invalidCurrency_returns400() throws Exception {

        String requestJson = """
                {
                    "merchantId": "merchant-001",
                    "amount": 100.00,
                    "currency": "DOLLAR",
                    "paymentMethod": "CARD"
                }
                """;

        mockMvc.perform(
                        post(API_PATH)
                                .header(IDEMPOTENCY_HEADER, UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    if (!body.contains("3-letter ISO")) {
                        throw new AssertionError(
                                "Expected currency ISO validation message in response but got: " + body);
                    }
                });
    }

    // ── TC-NEG-009: Invalid UUID path parameter ───────────────────────────────

    @Test
    @DisplayName("TC-NEG-009: Malformed UUID in path returns 400")
    void getPayment_malformedUuid_returns400() throws Exception {

        mockMvc.perform(get(API_PATH + "/not-a-valid-uuid"))
                .andExpect(status().isBadRequest());
    }
}