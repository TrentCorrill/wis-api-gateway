package com.wis.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration for API Gateway.
 *
 * <p>Allows requests from configured frontend applications. CORS (Cross-Origin Resource Sharing)
 * is necessary when the frontend and backend are served from different domains.
 *
 * <p>Configuration:
 * <pre>
 * cors:
 *   allowed-origins: https://wordsinseasonapp.com,https://www.wordsinseasonapp.com
 * </pre>
 *
 * <p>Features:
 * <ul>
 *   <li>Configurable allowed origins (supports wildcards like *.azurestaticapps.net)</li>
 *   <li>Allows standard HTTP methods (GET, POST, PUT, DELETE, OPTIONS, PATCH)</li>
 *   <li>Allows all headers (can be restricted if needed)</li>
 *   <li>Allows credentials (cookies, authorization headers)</li>
 *   <li>Caches preflight responses for 1 hour</li>
 * </ul>
 *
 * @author WIS Development Team
 * @version 1.0.0
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:https://*.azurestaticapps.net,https://wordsinseasonapp.com}")
    private String allowedOrigins;

    /**
     * Creates and configures the CORS web filter.
     *
     * @return configured CorsWebFilter bean
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Parse allowed origins from comma-separated string
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        config.setAllowedOriginPatterns(origins);

        // Allow standard HTTP methods
        config.setAllowedMethods(Arrays.asList(
            "GET",
            "POST",
            "PUT",
            "DELETE",
            "OPTIONS",
            "PATCH"
        ));

        // Allow all headers (can be restricted if needed for security)
        config.setAllowedHeaders(List.of("*"));

        // Allow credentials (cookies, authorization headers)
        config.setAllowCredentials(true);

        // Cache preflight response for 1 hour
        config.setMaxAge(3600L);

        // Apply CORS configuration to all paths
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
