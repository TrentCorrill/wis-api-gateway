package com.wis.apigateway.filter;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Resolves rate limiting key from API key header.
 *
 * <p>Each API key gets its own rate limit bucket. This ensures that different
 * clients (identified by their API keys) have independent rate limits.
 *
 * <p>Configuration in routes:
 * <pre>
 * filters:
 *   - name: RequestRateLimiter
 *     args:
 *       redis-rate-limiter.replenishRate: 10
 *       redis-rate-limiter.burstCapacity: 20
 *       key-resolver: "#{@apiKeyResolver}"
 * </pre>
 *
 * @author WIS Development Team
 * @version 1.0.0
 */
@Component("apiKeyResolver")
public class ApiKeyResolver implements KeyResolver {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String DEFAULT_KEY = "anonymous";

    /**
     * Resolves the rate limiting key from the request.
     *
     * @param exchange the current server web exchange
     * @return a Mono containing the API key or "anonymous" if not present
     */
    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String apiKey = exchange.getRequest()
            .getHeaders()
            .getFirst(API_KEY_HEADER);

        // Use API key as rate limit key, or "anonymous" if not present or empty
        // Note: Anonymous requests will still be blocked by ApiKeyAuthFilter
        // This is just a fallback for public endpoints
        return Mono.just(apiKey != null && !apiKey.isEmpty() ? apiKey : DEFAULT_KEY);
    }
}
