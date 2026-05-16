package com.yuno.payments.integration;

import com.yuno.payments.enums.*;
import com.yuno.payments.provider.ProviderConnectors.ProviderConnector;
import com.yuno.payments.routing.RoutingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Unit Tests: RoutingEngine Logic")
class RoutingEngineUnitTest {

    private RoutingEngine routingEngine;
    private ProviderConnector mockProviderA;
    private ProviderConnector mockProviderB;

    @BeforeEach
    void setUp() {

        mockProviderA = mock(ProviderConnector.class);
        when(mockProviderA.getProvider()).thenReturn(Provider.PROVIDER_A);

        mockProviderB = mock(ProviderConnector.class);
        when(mockProviderB.getProvider()).thenReturn(Provider.PROVIDER_B);
        routingEngine = new RoutingEngine(List.of(mockProviderA, mockProviderB));
    }

    @Test
    @DisplayName("CARD payment routes to Provider A")
    void resolve_cardPayment_returnsProviderA() {
        ProviderConnector result = routingEngine.resolve(PaymentMethod.CARD);

        assertThat(result).isSameAs(mockProviderA);
        assertThat(result.getProvider()).isEqualTo(Provider.PROVIDER_A);
    }

    @Test
    @DisplayName("UPI payment routes to Provider B")
    void resolve_upiPayment_returnsProviderB() {
        ProviderConnector result = routingEngine.resolve(PaymentMethod.UPI);

        assertThat(result).isSameAs(mockProviderB);
        assertThat(result.getProvider()).isEqualTo(Provider.PROVIDER_B);
    }

    @Test
    @DisplayName("Failover from Provider A returns Provider B")
    void resolveFailover_fromProviderA_returnsProviderB() {
        ProviderConnector failover = routingEngine.resolveFailover(Provider.PROVIDER_A);
        assertThat(failover.getProvider()).isEqualTo(Provider.PROVIDER_B);
    }

    @Test
    @DisplayName("Failover from Provider B returns Provider A")
    void resolveFailover_fromProviderB_returnsProviderA() {
        ProviderConnector failover = routingEngine.resolveFailover(Provider.PROVIDER_B);
        assertThat(failover.getProvider()).isEqualTo(Provider.PROVIDER_A);
    }

    @Test
    @DisplayName("Routing engine initializes with all registered providers")
    void routingEngine_registersAllProvidersAtStartup() {

        assertThatNoException().isThrownBy(
                () -> routingEngine.resolve(PaymentMethod.CARD)
        );
        assertThatNoException().isThrownBy(
                () -> routingEngine.resolve(PaymentMethod.UPI)
        );
    }
}