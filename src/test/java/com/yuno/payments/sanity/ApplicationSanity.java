package com.yuno.payments.sanity;

import com.yuno.payments.controller.PaymentController;
import com.yuno.payments.repository.PaymentRepository;
import com.yuno.payments.routing.RoutingEngine;
import com.yuno.payments.service.PaymentOrchestrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")  // Activates application-test.yml
@DisplayName("Sanity Tests: Application Context & Bean Wiring")
class ApplicationSanityTest {


    @Autowired
    private PaymentController paymentController;

    @Autowired
    private PaymentOrchestrationService orchestrationService;

    @Autowired
    private RoutingEngine routingEngine;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("Spring Application Context loads successfully")
    void contextLoads() {

    }

    @Test
    @DisplayName("All core beans are properly initialized (not null)")
    void coreBeansAreWired() {
        assertThat(paymentController)
                .as("PaymentController must be initialized")
                .isNotNull();

        assertThat(orchestrationService)
                .as("PaymentOrchestrationService must be initialized")
                .isNotNull();

        assertThat(routingEngine)
                .as("RoutingEngine must be initialized")
                .isNotNull();

        assertThat(paymentRepository)
                .as("PaymentRepository must be initialized")
                .isNotNull();
    }

    @Test
    @DisplayName("Database connection is healthy — repository can execute a count query")
    void databaseConnectionIsHealthy() {
        long count = paymentRepository.count();
        assertThat(count)
                .as("Initial payment count should be 0 on a fresh test database")
                .isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("RoutingEngine knows about both providers at startup")
    void routingEngineHasBothProviders() {

        var connectorA = routingEngine.resolve(com.yuno.payments.enums.PaymentMethod.CARD);
        var connectorB = routingEngine.resolve(com.yuno.payments.enums.PaymentMethod.UPI);

        assertThat(connectorA).isNotNull();
        assertThat(connectorB).isNotNull();
        assertThat(connectorA.getProvider()).isEqualTo(com.yuno.payments.enums.Provider.PROVIDER_A);
        assertThat(connectorB.getProvider()).isEqualTo(com.yuno.payments.enums.Provider.PROVIDER_B);
    }
}