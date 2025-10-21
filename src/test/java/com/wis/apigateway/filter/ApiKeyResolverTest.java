package com.wis.apigateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ApiKeyResolver.
 * Tests rate limiting key resolution from API keys.
 */
class ApiKeyResolverTest {

    private ApiKeyResolver resolver;

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String TEST_API_KEY = "test-key-12345";
    private static final String DEFAULT_KEY = "anonymous";

    @BeforeEach
    void setUp() {
        resolver = new ApiKeyResolver();
    }

    @Test
    void testResolveWithApiKey_ShouldReturnApiKey() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/test")
            .header(API_KEY_HEADER, TEST_API_KEY)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        Mono<String> result = resolver.resolve(exchange);

        // Assert
        StepVerifier.create(result)
            .assertNext(key -> assertThat(key).isEqualTo(TEST_API_KEY))
            .expectComplete()
            .verify();
    }

    @Test
    void testResolveWithoutApiKey_ShouldReturnDefault() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/test")
            .build(); // No API key header
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        Mono<String> result = resolver.resolve(exchange);

        // Assert
        StepVerifier.create(result)
            .assertNext(key -> assertThat(key).isEqualTo(DEFAULT_KEY))
            .expectComplete()
            .verify();
    }

    @Test
    void testResolveWithEmptyApiKey_ShouldReturnDefault() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/test")
            .header(API_KEY_HEADER, "")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        Mono<String> result = resolver.resolve(exchange);

        // Assert - Empty string should be treated as no key
        StepVerifier.create(result)
            .assertNext(key -> assertThat(key).isEqualTo(DEFAULT_KEY))
            .expectComplete()
            .verify();
    }

    @Test
    void testResolveDifferentApiKeys_ShouldReturnDifferentKeys() {
        // Arrange - First request
        MockServerHttpRequest request1 = MockServerHttpRequest
            .get("/api/test")
            .header(API_KEY_HEADER, "key-1")
            .build();
        MockServerWebExchange exchange1 = MockServerWebExchange.from(request1);

        // Arrange - Second request
        MockServerHttpRequest request2 = MockServerHttpRequest
            .get("/api/test")
            .header(API_KEY_HEADER, "key-2")
            .build();
        MockServerWebExchange exchange2 = MockServerWebExchange.from(request2);

        // Act
        Mono<String> result1 = resolver.resolve(exchange1);
        Mono<String> result2 = resolver.resolve(exchange2);

        // Assert - Different API keys should resolve to different rate limit keys
        StepVerifier.create(result1)
            .assertNext(key -> assertThat(key).isEqualTo("key-1"))
            .expectComplete()
            .verify();

        StepVerifier.create(result2)
            .assertNext(key -> assertThat(key).isEqualTo("key-2"))
            .expectComplete()
            .verify();
    }

    @Test
    void testResolveSameApiKeyMultipleTimes_ShouldReturnSameKey() {
        // Arrange - Two requests with same API key
        MockServerHttpRequest request1 = MockServerHttpRequest
            .get("/api/test1")
            .header(API_KEY_HEADER, TEST_API_KEY)
            .build();
        MockServerWebExchange exchange1 = MockServerWebExchange.from(request1);

        MockServerHttpRequest request2 = MockServerHttpRequest
            .get("/api/test2")
            .header(API_KEY_HEADER, TEST_API_KEY)
            .build();
        MockServerWebExchange exchange2 = MockServerWebExchange.from(request2);

        // Act
        Mono<String> result1 = resolver.resolve(exchange1);
        Mono<String> result2 = resolver.resolve(exchange2);

        // Assert - Same API key should always resolve to same rate limit key
        StepVerifier.create(result1)
            .assertNext(key -> assertThat(key).isEqualTo(TEST_API_KEY))
            .expectComplete()
            .verify();

        StepVerifier.create(result2)
            .assertNext(key -> assertThat(key).isEqualTo(TEST_API_KEY))
            .expectComplete()
            .verify();
    }

    @Test
    void testResolveWithNullHeader_ShouldReturnDefault() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/test")
            .header(API_KEY_HEADER, (String) null)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        Mono<String> result = resolver.resolve(exchange);

        // Assert
        StepVerifier.create(result)
            .assertNext(key -> assertThat(key).isEqualTo(DEFAULT_KEY))
            .expectComplete()
            .verify();
    }
}
