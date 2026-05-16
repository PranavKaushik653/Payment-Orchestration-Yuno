package com.yuno.payments.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuno.payments.model.Payment;
import com.yuno.payments.dto.*;
import com.yuno.payments.enums.*;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Integration Tests: Full Request Lifecycle")
class PaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RoutingEngine routingEngine;

    private ProviderConnector mockConnector;

    private static final String API_PATH = "/api/v1/payments";
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    @BeforeEach
    void setUp() {
        mockConnector = mock(ProviderConnector.class);
        when(mockConnector.getProvider()).thenReturn(Provider.PROVIDER_A);
        when(routingEngine.resolve(any(PaymentMethod.class))).thenReturn(mockConnector);
        when(routingEngine.getProvider(any())).thenReturn(Provider.PROVIDER_A);
    }

    @Test
    @DisplayName("TC-INT-001: Successful CARD payment — full happy path")
    void createCardPayment_success_returns201() throws Exception {
        when(mockConnector.process(any(Payment.class)))
                .thenReturn(ProviderResult.success("PA-TXN-12345"));

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .merchantId("merchant-001")
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .paymentMethod(PaymentMethod.CARD)
                .build();

        String idempotencyKey = UUID.randomUUID().toString();

        MvcResult result = mockMvc.perform(
                        post(API_PATH)
                                .header(IDEMPOTENCY_HEADER, idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").exists())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.merchantId").value("merchant-001"))
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.providerTransactionId").value("PA-TXN-12345"))
                .andReturn();

        verify(mockConnector, times(1)).process(any(Payment.class));

        String json = result.getResponse().getContentAsString();
        PaymentResponse response = objectMapper.readValue(
                json, PaymentResponse.class);

        assertThat(response.getPaymentId()).isNotNull();
        assertThat(response.getAssignedProvider()).isEqualTo(Provider.PROVIDER_A);
    }

    @Test
    @DisplayName("TC-INT-002: Successful UPI payment routes to Provider B")
    void createUpiPayment_routesToProviderB() throws Exception {
        ProviderConnector mockProviderB = mock(ProviderConnector.class);
        when(mockProviderB.getProvider()).thenReturn(Provider.PROVIDER_B);
        when(mockProviderB.process(any())).thenReturn(ProviderResult.success("PB-TXN-99999"));

        when(routingEngine.resolve(PaymentMethod.UPI)).thenReturn(mockProviderB);

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .merchantId("merchant-002")
                .amount(new BigDecimal("500.00"))
                .currency("INR")
                .paymentMethod(PaymentMethod.UPI)
                .build();

        mockMvc.perform(
                        post(API_PATH)
                                .header(IDEMPOTENCY_HEADER, UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.providerTransactionId").value("PB-TXN-99999"));

        verify(mockProviderB, times(1)).process(any());
        verify(mockConnector, never()).process(any());
    }

    @Test
    @DisplayName("TC-INT-003: Fetch payment by ID returns correct data")
    void fetchPaymentById_returnsCorrectPayment() throws Exception {
        when(mockConnector.process(any())).thenReturn(ProviderResult.success("PA-TXN-FETCH"));

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .merchantId("merchant-fetch")
                .amount(new BigDecimal("75.00"))
                .currency("USD")
                .paymentMethod(PaymentMethod.CARD)
                .build();

        MvcResult createResult = mockMvc.perform(
                        post(API_PATH)
                                .header(IDEMPOTENCY_HEADER, UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andReturn();

        PaymentResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                PaymentResponse.class);
        UUID paymentId = created.getPaymentId();

        mockMvc.perform(get(API_PATH + "/" + paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.merchantId").value("merchant-fetch"))
                .andExpect(jsonPath("$.amount").value(75.00))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
}
