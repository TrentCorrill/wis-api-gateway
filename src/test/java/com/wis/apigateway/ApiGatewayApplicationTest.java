package com.wis.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for API Gateway Application.
 * Verifies that the Spring Boot application context loads successfully.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApiGatewayApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        // Verify application context loads successfully
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void verifyMainClassExists() {
        // Verify main application class is present
        assertThat(ApiGatewayApplication.class).isNotNull();
    }
}
