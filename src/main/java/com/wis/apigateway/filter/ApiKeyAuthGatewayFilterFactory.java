package com.wis.apigateway.filter;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Gateway filter for API key authentication.
 *
 * <p>Validates X-API-Key header against configured keys. Returns 401 Unauthorized
 * if the API key is missing or invalid.
 *
 * <p>Configuration:
 * <pre>
 * api:
 *   keys: key1,key2,key3  # Comma-separated list of valid API keys
 * </pre>
 *
 * <p>Usage in routes:
 * <pre>
 * filters:
 *   - name: ApiKeyAuth
 * </pre>
 *
 * @author WIS Development Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class ApiKeyAuthGatewayFilterFactory
    extends AbstractGatewayFilterFactory<ApiKeyAuthGatewayFilterFactory.Config> {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Value("${api.keys:}")
    private String apiKeys;

    public ApiKeyAuthGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Extract API key from header
            String apiKey = exchange.getRequest()
                .getHeaders()
                .getFirst(API_KEY_HEADER);

            // Validate API key presence
            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("Request missing API key: {} {} from IP: {}",
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath(),
                    exchange.getRequest().getRemoteAddress());

                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // Validate API key value
            if (!isValidApiKey(apiKey)) {
                log.warn("Invalid API key attempt: {} from IP: {} for path: {}",
                    maskApiKey(apiKey),
                    exchange.getRequest().getRemoteAddress(),
                    exchange.getRequest().getPath());

                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // API key valid, proceed with request
            log.debug("API key validated successfully for request: {} {}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath());

            return chain.filter(exchange);
        };
    }

    /**
     * Validates API key against configured keys.
     *
     * @param key the API key to validate
     * @return true if the key is valid, false otherwise
     */
    private boolean isValidApiKey(String key) {
        if (apiKeys == null || apiKeys.isEmpty()) {
            log.error("No API keys configured! All requests will be rejected.");
            return false;
        }

        List<String> validKeys = Arrays.asList(apiKeys.split(","));
        return validKeys.stream()
            .map(String::trim)
            .anyMatch(validKey -> validKey.equals(key));
    }

    /**
     * Masks API key for logging (shows first 4 and last 4 characters).
     *
     * @param key the API key to mask
     * @return masked API key (e.g., "abcd****wxyz")
     */
    private String maskApiKey(String key) {
        if (key == null || key.length() < 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    /**
     * Configuration class for this filter.
     * Currently empty but allows for future configuration options.
     */
    @Data
    public static class Config {
        // Configuration properties can be added here if needed
    }
}
