package com.wis.apigateway.filter;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

/**
 * Gateway filter for adding service-to-service authentication key.
 *
 * <p>Adds X-Service-Key header to requests forwarded to backend services.
 * This enables backend services to validate that requests come from the API Gateway.
 *
 * <p>Usage in routes:
 * <pre>
 * filters:
 *   - name: ServiceKey
 * </pre>
 *
 * @author WIS Development Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class ServiceKeyGatewayFilterFactory
    extends AbstractGatewayFilterFactory<ServiceKeyGatewayFilterFactory.Config> {

    private static final String SERVICE_KEY_HEADER = "X-Service-Key";

    @Value("${SERVICE_TO_SERVICE_KEY:}")
    private String serviceKey;

    public ServiceKeyGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            if (serviceKey == null || serviceKey.isEmpty()) {
                log.warn("SERVICE_TO_SERVICE_KEY not configured - backend services will reject requests");
            } else {
                log.debug("Adding {} header to request", SERVICE_KEY_HEADER);
                exchange = exchange.mutate()
                    .request(r -> r.header(SERVICE_KEY_HEADER, serviceKey))
                    .build();
            }

            return chain.filter(exchange);
        };
    }

    /**
     * Configuration class for this filter.
     */
    @Data
    public static class Config {
        // Configuration properties can be added here if needed
    }
}
