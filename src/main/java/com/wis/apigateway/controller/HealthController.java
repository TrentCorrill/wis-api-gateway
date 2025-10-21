package com.wis.apigateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Health check controller for API Gateway and downstream services.
 *
 * <p>Provides aggregated health checks for all backend services. Useful for
 * monitoring the overall system health from a single endpoint.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /health/all - Aggregated health of all services</li>
 * </ul>
 *
 * @author WIS Development Team
 * @version 1.0.0
 */
@Slf4j
@RestController
public class HealthController {

    @Value("${backend.registration.url}")
    private String registrationUrl;

    @Value("${backend.subscriptions.url}")
    private String subscriptionsUrl;

    @Value("${backend.messages.url}")
    private String messagesUrl;

    private final WebClient webClient = WebClient.builder().build();

    /**
     * Aggregated health check for all backend services.
     *
     * <p>Calls the health endpoint of each downstream service and returns a combined
     * health status. If any service is down, the overall status is DOWN.
     *
     * <p>This endpoint is public and does not require authentication.
     *
     * @return ResponseEntity containing aggregated health status
     */
    @GetMapping("/health/all")
    public Mono<ResponseEntity<Map<String, Object>>> aggregateHealth() {
        log.info("Aggregated health check requested");

        return Mono.zip(
            checkService("registration", registrationUrl + "/actuator/health"),
            checkService("subscriptions", subscriptionsUrl + "/actuator/health"),
            checkService("messages", messagesUrl + "/actuator/health")
        ).map(tuple -> {
            Map<String, Object> health = new HashMap<>();
            Map<String, Object> services = new HashMap<>();

            services.put("registration", tuple.getT1());
            services.put("subscriptions", tuple.getT2());
            services.put("messages", tuple.getT3());

            // Determine overall status
            boolean allHealthy = services.values().stream()
                .allMatch(service -> {
                    if (service instanceof Map) {
                        return "UP".equals(((Map<?, ?>) service).get("status"));
                    }
                    return false;
                });

            health.put("status", allHealthy ? "UP" : "DOWN");
            health.put("services", services);
            health.put("timestamp", System.currentTimeMillis());

            log.info("Aggregated health check completed: {}", allHealthy ? "UP" : "DOWN");

            return ResponseEntity.ok(health);
        }).onErrorResume(e -> {
            log.error("Health check failed", e);

            Map<String, Object> health = new HashMap<>();
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            health.put("timestamp", System.currentTimeMillis());

            return Mono.just(ResponseEntity.status(503).body(health));
        });
    }

    /**
     * Checks the health of a single service.
     *
     * @param name service name for logging
     * @param url health endpoint URL
     * @return Mono containing service health status
     */
    private Mono<Map<String, String>> checkService(String name, String url) {
        return webClient.get()
            .uri(url)
            .retrieve()
            .bodyToMono(Map.class)
            .map(response -> Map.of(
                "status", response.getOrDefault("status", "UNKNOWN").toString(),
                "url", url
            ))
            .onErrorReturn(Map.of(
                "status", "DOWN",
                "url", url,
                "error", "Failed to connect"
            ))
            .timeout(java.time.Duration.ofSeconds(5))
            .doOnSuccess(status -> log.debug("Health check for {} : {}", name, status.get("status")))
            .doOnError(e -> log.warn("Health check failed for {}: {}", name, e.getMessage()));
    }
}
