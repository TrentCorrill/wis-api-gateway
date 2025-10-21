package com.wis.apigateway.filter;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Gateway filter for logging requests and responses.
 *
 * <p>Logs information about each request and response passing through the gateway.
 * Useful for debugging, monitoring, and auditing.
 *
 * <p>Logged information includes:
 * <ul>
 *   <li>Request: HTTP method, path, remote IP address</li>
 *   <li>Response: HTTP status code, processing duration</li>
 * </ul>
 *
 * <p>Usage in routes:
 * <pre>
 * filters:
 *   - name: Logging
 * </pre>
 *
 * @author WIS Development Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class LoggingGatewayFilterFactory
    extends AbstractGatewayFilterFactory<LoggingGatewayFilterFactory.Config> {

    public LoggingGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            long startTime = System.currentTimeMillis();

            // Log incoming request
            log.info("Request: {} {} from {}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath(),
                exchange.getRequest().getRemoteAddress());

            // Continue filter chain and log response
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                long duration = System.currentTimeMillis() - startTime;

                log.info("Response: {} {} - Status: {} - Duration: {}ms",
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath(),
                    exchange.getResponse().getStatusCode(),
                    duration);
            }));
        };
    }

    /**
     * Configuration class for this filter.
     * Currently empty but allows for future configuration options.
     */
    @Data
    public static class Config {
        // Configuration properties can be added here if needed
        // For example: logHeaders, logBody, etc.
    }
}
