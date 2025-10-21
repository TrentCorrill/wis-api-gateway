package com.wis.apigateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for API Gateway.
 *
 * <p>Catches all unhandled exceptions and returns standardized JSON error responses.
 * Prevents stack traces from being exposed to clients while logging full details
 * for debugging.
 *
 * <p>Features:
 * <ul>
 *   <li>Standardized JSON error format</li>
 *   <li>Appropriate HTTP status codes</li>
 *   <li>Full exception logging with context</li>
 *   <li>User-friendly error messages</li>
 * </ul>
 *
 * @author WIS Development Team
 * @version 1.0.0
 */
@Slf4j
@Component
@Order(-1)  // High precedence to catch all exceptions
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Handles all exceptions that occur during request processing.
     *
     * @param exchange the current server web exchange
     * @param ex the exception that occurred
     * @return Mono that completes when error response is written
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // Log full error details for debugging
        log.error("Gateway error occurred: {} - {} - {}",
            exchange.getRequest().getMethod(),
            exchange.getRequest().getPath(),
            ex.getMessage(),
            ex);

        // Set response content type
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Determine appropriate HTTP status and message
        HttpStatus status = determineHttpStatus(ex);
        String message = determineErrorMessage(ex);

        // Set response status
        exchange.getResponse().setStatusCode(status);

        // Build error response
        Map<String, Object> errorResponse = buildErrorResponse(
            status,
            message,
            exchange.getRequest().getPath().value()
        );

        // Write response
        try {
            byte[] bytes = objectMapper.writeValueAsString(errorResponse)
                .getBytes(StandardCharsets.UTF_8);

            return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(bytes))
            );
        } catch (Exception e) {
            log.error("Error writing error response", e);
            return Mono.empty();
        }
    }

    /**
     * Determines the appropriate HTTP status code based on the exception type.
     *
     * @param ex the exception
     * @return appropriate HTTP status
     */
    private HttpStatus determineHttpStatus(Throwable ex) {
        if (ex instanceof org.springframework.web.server.ResponseStatusException) {
            return HttpStatus.valueOf(
                ((org.springframework.web.server.ResponseStatusException) ex).getStatusCode().value()
            );
        } else if (ex instanceof java.nio.file.AccessDeniedException) {
            return HttpStatus.FORBIDDEN;
        } else if (ex instanceof IllegalArgumentException) {
            return HttpStatus.BAD_REQUEST;
        } else if (ex instanceof org.springframework.web.server.ServerWebInputException) {
            return HttpStatus.BAD_REQUEST;
        } else {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    /**
     * Determines a user-friendly error message based on the exception.
     *
     * @param ex the exception
     * @return user-friendly error message
     */
    private String determineErrorMessage(Throwable ex) {
        if (ex instanceof org.springframework.web.server.ResponseStatusException) {
            return ex.getMessage();
        } else if (ex instanceof java.nio.file.AccessDeniedException) {
            return "Access denied";
        } else if (ex instanceof IllegalArgumentException) {
            return "Invalid request: " + ex.getMessage();
        } else {
            // Don't expose internal error details to clients
            return "An unexpected error occurred. Please try again later.";
        }
    }

    /**
     * Builds the standardized error response object.
     *
     * @param status HTTP status code
     * @param message error message
     * @param path request path
     * @return map containing error response data
     */
    private Map<String, Object> buildErrorResponse(HttpStatus status, String message, String path) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", Instant.now().toString());
        errorResponse.put("status", status.value());
        errorResponse.put("error", status.getReasonPhrase());
        errorResponse.put("message", message);
        errorResponse.put("path", path);
        return errorResponse;
    }
}
