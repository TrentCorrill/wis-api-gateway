package com.wis.apigateway.config;

import org.springframework.context.annotation.Configuration;

/**
 * Redis configuration for rate limiting.
 *
 * <p>This class serves as a placeholder for Redis configuration. Spring Boot's
 * auto-configuration handles Redis connection based on properties in application.yml.
 *
 * <p>Configuration properties:
 * <pre>
 * spring:
 *   data:
 *     redis:
 *       host: localhost
 *       port: 6379
 *       password: (optional)
 *       timeout: 2000ms
 *       ssl:
 *         enabled: false  # Set to true for Azure Redis
 * </pre>
 *
 * <p>Custom Redis configuration can be added here if needed, such as:
 * <ul>
 *   <li>Custom connection factory</li>
 *   <li>Connection pooling settings</li>
 *   <li>Serialization configuration</li>
 *   <li>Redis template customization</li>
 * </ul>
 *
 * @author WIS Development Team
 * @version 1.0.0
 */
@Configuration
public class RedisConfig {

    // Redis configuration is handled by Spring Boot auto-configuration
    // based on spring.data.redis.* properties

    // Custom Redis beans can be added here if needed:
    //
    // @Bean
    // public RedisConnectionFactory redisConnectionFactory() {
    //     // Custom connection factory configuration
    // }
    //
    // @Bean
    // public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
    //         ReactiveRedisConnectionFactory factory) {
    //     // Custom template configuration
    // }
}
