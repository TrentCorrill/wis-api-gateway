package com.wis.apigateway.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.wis.apigateway.filter.LoggingGatewayFilterFactory.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LoggingGatewayFilterFactory.
 * Tests request/response logging functionality.
 */
class LoggingGatewayFilterFactoryTest {

    private LoggingGatewayFilterFactory filterFactory;

    @Mock
    private GatewayFilterChain mockChain;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filterFactory = new LoggingGatewayFilterFactory();

        // Set up log capture
        logger = (Logger) LoggerFactory.getLogger(LoggingGatewayFilterFactory.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        // Mock chain to return completed Mono
        when(mockChain.filter(any(ServerWebExchange.class)))
            .thenAnswer(invocation -> {
                ServerWebExchange exchange = invocation.getArgument(0);
                exchange.getResponse().setStatusCode(HttpStatus.OK);
                return Mono.empty();
            });
    }

    @AfterEach
    void tearDown() {
        listAppender.stop();
        logger.detachAppender(listAppender);
    }

    @Test
    void testLogsRequestAndResponse() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .method(HttpMethod.GET, "/api/test")
            .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new Config());

        // Act
        Mono<Void> result = filter.filter(exchange, mockChain);

        // Assert
        StepVerifier.create(result)
            .expectComplete()
            .verify();

        // Verify logs
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSizeGreaterThanOrEqualTo(2);

        // Check request log
        ILoggingEvent requestLog = logsList.get(0);
        assertThat(requestLog.getFormattedMessage())
            .contains("Request:")
            .contains("GET")
            .contains("/api/test")
            .contains("127.0.0.1");

        // Check response log
        ILoggingEvent responseLog = logsList.get(1);
        assertThat(responseLog.getFormattedMessage())
            .contains("Response:")
            .contains("GET")
            .contains("/api/test")
            .contains("200")
            .contains("Duration:");
    }

    @Test
    void testLogsDifferentHttpMethods() {
        // Test POST
        MockServerHttpRequest postRequest = MockServerHttpRequest
            .method(HttpMethod.POST, "/api/create")
            .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
            .build();
        MockServerWebExchange postExchange = MockServerWebExchange.from(postRequest);

        GatewayFilter filter = filterFactory.apply(new Config());

        StepVerifier.create(filter.filter(postExchange, mockChain))
            .expectComplete()
            .verify();

        // Verify POST was logged
        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs.get(0).getFormattedMessage())
            .contains("POST")
            .contains("/api/create");

        listAppender.list.clear();

        // Test DELETE
        MockServerHttpRequest deleteRequest = MockServerHttpRequest
            .method(HttpMethod.DELETE, "/api/delete/123")
            .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
            .build();
        MockServerWebExchange deleteExchange = MockServerWebExchange.from(deleteRequest);

        StepVerifier.create(filter.filter(deleteExchange, mockChain))
            .expectComplete()
            .verify();

        // Verify DELETE was logged
        assertThat(listAppender.list.get(0).getFormattedMessage())
            .contains("DELETE")
            .contains("/api/delete/123");
    }

    @Test
    void testLogsErrorStatus() {
        // Mock chain to return error status
        when(mockChain.filter(any(ServerWebExchange.class)))
            .thenAnswer(invocation -> {
                ServerWebExchange exchange = invocation.getArgument(0);
                exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                return Mono.empty();
            });

        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/error")
            .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new Config());

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()
            .verify();

        // Verify error status was logged
        List<ILoggingEvent> logs = listAppender.list;
        ILoggingEvent responseLog = logs.stream()
            .filter(log -> log.getFormattedMessage().contains("Response:"))
            .findFirst()
            .orElseThrow();

        assertThat(responseLog.getFormattedMessage())
            .contains("500");
    }

    @Test
    void testLogsDuration() throws InterruptedException {
        // Mock chain to simulate delay
        when(mockChain.filter(any(ServerWebExchange.class)))
            .thenAnswer(invocation -> {
                ServerWebExchange exchange = invocation.getArgument(0);
                exchange.getResponse().setStatusCode(HttpStatus.OK);
                return Mono.delay(java.time.Duration.ofMillis(50))
                    .then();
            });

        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/slow")
            .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new Config());

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()
            .verify();

        // Verify duration was logged
        List<ILoggingEvent> logs = listAppender.list;
        ILoggingEvent responseLog = logs.stream()
            .filter(log -> log.getFormattedMessage().contains("Duration:"))
            .findFirst()
            .orElseThrow();

        String message = responseLog.getFormattedMessage();
        assertThat(message).contains("ms");

        // Extract duration (format: "Duration: XXms")
        String durationStr = message.substring(message.indexOf("Duration:") + 10);
        durationStr = durationStr.substring(0, durationStr.indexOf("ms"));
        int duration = Integer.parseInt(durationStr.trim());

        // Verify duration is reasonable (at least 50ms due to delay)
        assertThat(duration).isGreaterThanOrEqualTo(50);
    }

    @Test
    void testLogsWithQueryParameters() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/search?query=test&page=1")
            .remoteAddress(new InetSocketAddress("192.168.1.1", 54321))
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new Config());

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectComplete()
            .verify();

        // Verify query parameters path is logged
        List<ILoggingEvent> logs = listAppender.list;
        ILoggingEvent requestLog = logs.get(0);
        // The path includes query parameters
        assertThat(requestLog.getFormattedMessage())
            .contains("/api/search");
    }
}
