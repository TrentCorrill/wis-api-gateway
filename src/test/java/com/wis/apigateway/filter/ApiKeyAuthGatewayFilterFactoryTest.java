package com.wis.apigateway.filter;

import com.wis.apigateway.filter.ApiKeyAuthGatewayFilterFactory.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ApiKeyAuthGatewayFilterFactory.
 * Tests API key validation and authentication logic.
 */
class ApiKeyAuthGatewayFilterFactoryTest {

    private ApiKeyAuthGatewayFilterFactory filterFactory;

    @Mock
    private GatewayFilterChain mockChain;

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String VALID_API_KEY = "test-key-12345";
    private static final String INVALID_API_KEY = "invalid-key";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filterFactory = new ApiKeyAuthGatewayFilterFactory();

        // Set test API keys
        ReflectionTestUtils.setField(filterFactory, "apiKeys", "test-key-12345,another-valid-key");

        // Mock chain to return completed Mono
        when(mockChain.filter(any(ServerWebExchange.class)))
            .thenReturn(Mono.empty());
    }

    @Test
    void testValidApiKey_ShouldAllowRequest() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/test")
            .header(API_KEY_HEADER, VALID_API_KEY)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new Config());

        // Act
        Mono<Void> result = filter.filter(exchange, mockChain);

        // Assert
        StepVerifier.create(result)
            .expectComplete()
            .verify();

        assertThat(exchange.getResponse().getStatusCode()).isNull(); // No error status set
    }

    @Test
    void testInvalidApiKey_ShouldReturnUnauthorized() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/test")
            .header(API_KEY_HEADER, INVALID_API_KEY)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new Config());

        // Act
        Mono<Void> result = filter.filter(exchange, mockChain);

        // Assert
        StepVerifier.create(result)
            .expectComplete()
            .verify();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testMissingApiKey_ShouldReturnUnauthorized() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/test")
            .build(); // No API key header
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new Config());

        // Act
        Mono<Void> result = filter.filter(exchange, mockChain);

        // Assert
        StepVerifier.create(result)
            .expectComplete()
            .verify();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testEmptyApiKey_ShouldReturnUnauthorized() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/test")
            .header(API_KEY_HEADER, "")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new Config());

        // Act
        Mono<Void> result = filter.filter(exchange, mockChain);

        // Assert
        StepVerifier.create(result)
            .expectComplete()
            .verify();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testMultipleValidApiKeys_ShouldAllowBoth() {
        // Arrange - Test first key
        MockServerHttpRequest request1 = MockServerHttpRequest
            .get("/api/test")
            .header(API_KEY_HEADER, "test-key-12345")
            .build();
        MockServerWebExchange exchange1 = MockServerWebExchange.from(request1);

        GatewayFilter filter = filterFactory.apply(new Config());

        // Act
        Mono<Void> result1 = filter.filter(exchange1, mockChain);

        // Assert
        StepVerifier.create(result1)
            .expectComplete()
            .verify();
        assertThat(exchange1.getResponse().getStatusCode()).isNull();

        // Arrange - Test second key
        MockServerHttpRequest request2 = MockServerHttpRequest
            .get("/api/test")
            .header(API_KEY_HEADER, "another-valid-key")
            .build();
        MockServerWebExchange exchange2 = MockServerWebExchange.from(request2);

        // Act
        Mono<Void> result2 = filter.filter(exchange2, mockChain);

        // Assert
        StepVerifier.create(result2)
            .expectComplete()
            .verify();
        assertThat(exchange2.getResponse().getStatusCode()).isNull();
    }

    @Test
    void testApiKeyWithWhitespace_ShouldBeHandled() {
        // Arrange - API keys might have whitespace in config
        ReflectionTestUtils.setField(filterFactory, "apiKeys", " test-key-12345 , another-valid-key ");

        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/test")
            .header(API_KEY_HEADER, "test-key-12345")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new Config());

        // Act
        Mono<Void> result = filter.filter(exchange, mockChain);

        // Assert
        StepVerifier.create(result)
            .expectComplete()
            .verify();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void testNoApiKeysConfigured_ShouldRejectAll() {
        // Arrange - Empty API keys configuration
        ReflectionTestUtils.setField(filterFactory, "apiKeys", "");

        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/test")
            .header(API_KEY_HEADER, VALID_API_KEY)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new Config());

        // Act
        Mono<Void> result = filter.filter(exchange, mockChain);

        // Assert
        StepVerifier.create(result)
            .expectComplete()
            .verify();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
