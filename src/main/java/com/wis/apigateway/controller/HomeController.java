package com.wis.apigateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Home controller for API Gateway information.
 *
 * <p>Provides basic information about the API Gateway service, including
 * version, available endpoints, and documentation links.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET / - Service information</li>
 * </ul>
 *
 * @author WIS Development Team
 * @version 1.0.0
 */
@RestController
public class HomeController {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${application.version:1.0.0}")
    private String version;

    /**
     * Returns API Gateway service information.
     *
     * <p>This endpoint is public and does not require authentication.
     *
     * @return map containing service information
     */
    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
            "service", applicationName,
            "version", version,
            "description", "API Gateway for Words in Season Platform",
            "endpoints", Map.of(
                "health", "/actuator/health",
                "aggregatedHealth", "/health/all",
                "metrics", "/actuator/metrics",
                "registration", "/api/register/**",
                "subscriptions", "/api/subscriptions/**",
                "messages", "/api/messages/**"
            ),
            "documentation", Map.of(
                "readme", "https://github.com/wis/api-gateway/README.md",
                "api", "/v3/api-docs"
            ),
            "authentication", Map.of(
                "header", "X-API-Key",
                "required", true,
                "note", "All /api/** endpoints require API key authentication"
            )
        );
    }
}
