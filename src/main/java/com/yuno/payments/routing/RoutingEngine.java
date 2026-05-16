package com.yuno.payments.routing;

import com.yuno.payments.enums.PaymentMethod;
import com.yuno.payments.enums.Provider;
import com.yuno.payments.provider.ProviderConnectors.ProviderConnector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class RoutingEngine {

    private static final Map<PaymentMethod, Provider> ROUTING_TABLE = Map.of(
            PaymentMethod.CARD, Provider.PROVIDER_A,
            PaymentMethod.UPI,  Provider.PROVIDER_B
    );

    private static final Map<Provider, Provider> FAILOVER_TABLE = Map.of(
            Provider.PROVIDER_A, Provider.PROVIDER_B,
            Provider.PROVIDER_B, Provider.PROVIDER_A
    );

    private final Map<Provider, ProviderConnector> connectorRegistry;

    public RoutingEngine(List<ProviderConnector> connectors) {
        this.connectorRegistry = connectors.stream()
                .collect(Collectors.toMap(
                        ProviderConnector::getProvider,
                        Function.identity()
                ));
        log.info("RoutingEngine initialized with providers: {}", connectorRegistry.keySet());
    }

    public ProviderConnector resolve(PaymentMethod paymentMethod) {
        Provider provider = ROUTING_TABLE.get(paymentMethod);
        if (provider == null) {
            throw new IllegalArgumentException("No routing rule for payment method: " + paymentMethod);
        }

        ProviderConnector connector = connectorRegistry.get(provider);
        if (connector == null) {
            throw new IllegalStateException("No connector registered for provider: " + provider);
        }

        log.debug("Routing {} → {}", paymentMethod, provider);
        return connector;
    }

    public ProviderConnector resolveFailover(Provider failedProvider) {
        Provider failoverProvider = FAILOVER_TABLE.get(failedProvider);
        if (failoverProvider == null) {
            throw new IllegalStateException("No failover configured for provider: " + failedProvider);
        }

        log.warn("Failover activated: {} → {}", failedProvider, failoverProvider);
        return connectorRegistry.get(failoverProvider);
    }

    public Provider getProvider(PaymentMethod paymentMethod) {
        return ROUTING_TABLE.get(paymentMethod);
    }
}