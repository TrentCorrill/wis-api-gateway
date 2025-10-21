package com.wis.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for WIS API Gateway.
 *
 * <p>This Spring Cloud Gateway application serves as the unified entry point for all
 * Words in Season backend microservices. It provides:
 * <ul>
 *   <li>API key authentication for protected routes</li>
 *   <li>Redis-backed rate limiting per API key</li>
 *   <li>Request routing to appropriate backend services</li>
 *   <li>CORS handling for web applications</li>
 *   <li>Special routing for webhooks (bypasses authentication)</li>
 *   <li>Aggregated health checks for all downstream services</li>
 * </ul>
 *
 * @author WIS Development Team
 * @version 1.0.0
 * @since 2025-10-21
 */
@SpringBootApplication
public class ApiGatewayApplication {

    /**
     * Main entry point for the application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
