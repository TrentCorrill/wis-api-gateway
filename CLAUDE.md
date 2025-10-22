# CLAUDE.md

This file provides guidance to Claude Code when working with the WIS API Gateway.

## Project Overview

The **WIS API Gateway** is a Spring Cloud Gateway application that serves as the unified entry point for all Words in Season backend microservices. It provides:
- API key authentication
- Rate limiting (Redis-backed)
- Request routing to backend services
- CORS handling
- Webhook routing (Stripe, Twilio)
- Health check aggregation

## Tech Stack
- **Java 17** with **Spring Boot 3.2.1**
- **Spring Cloud Gateway 2023.0.0**
- **Gradle** for build management
- **Project Lombok** for reducing boilerplate
- **Redis** for rate limiting
- **Azure**: App Service, Key Vault, Application Insights

## Development Commands

### Core Commands
```bash
# Build project
./gradlew build

# Run tests
./gradlew test

# Run locally (uses local profile)
./gradlew runLocal

# Run with specific profile
./gradlew bootRun --args='--spring.profiles.active=stage'

# Clean build
./gradlew clean build
```

### Docker Commands
```bash
# Build Docker image
docker build -t wis-api-gateway:latest .

# Run with Docker Compose (includes Redis)
docker-compose up

# Stop services
docker-compose down

# Run Redis only
docker run -d -p 6379:6379 redis:7-alpine
```

### Testing Commands
```bash
# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests ApiKeyAuthFilterTests

# Run with coverage
./gradlew test jacocoTestReport

# Test API Gateway locally
curl http://localhost:8080/ \
  -H "X-API-Key: local-dev-key-12345"

# Test rate limiting
for i in {1..15}; do curl -X GET http://localhost:8080/api/register/health \
  -H "X-API-Key: local-dev-key-12345"; done
```

## Project Structure

```
src/main/java/com/wis/apigateway/
├── ApiGatewayApplication.java        # Main application class
├── config/
│   ├── CorsConfig.java              # CORS configuration
│   └── RedisConfig.java             # Redis configuration (auto-config)
├── filter/
│   ├── ApiKeyAuthGatewayFilterFactory.java  # API key authentication filter
│   ├── ApiKeyResolver.java                  # Rate limiting key resolver
│   └── LoggingGatewayFilterFactory.java     # Request/response logging
├── controller/
│   ├── HealthController.java        # Aggregated health checks
│   └── HomeController.java          # Gateway info endpoint
└── exception/
    └── GlobalExceptionHandler.java  # Global error handling

src/main/resources/
├── application.yml           # Base configuration with all routes
├── application-local.yml     # Local dev (localhost backend services)
├── application-stage.yml     # Azure staging environment
└── application-prod.yml      # Azure production environment
```

## Key Components

### 1. Filters (Gateway Logic)

**ApiKeyAuthGatewayFilterFactory** (`filter/ApiKeyAuthGatewayFilterFactory.java`)
- Validates `X-API-Key` header
- Returns 401 if missing or invalid
- Supports multiple API keys (comma-separated)
- Masks API keys in logs

**ApiKeyResolver** (`filter/ApiKeyResolver.java`)
- Resolves rate limiting key from `X-API-Key` header
- Returns "anonymous" if no key present
- Used by Redis rate limiter

**LoggingGatewayFilterFactory** (`filter/LoggingGatewayFilterFactory.java`)
- Logs request method, path, IP
- Logs response status and duration
- Helps with debugging and monitoring

### 2. Routes (in application.yml)

```yaml
spring.cloud.gateway.routes:
  # Protected routes (require API key)
  - id: registration
    uri: ${backend.registration.url}
    predicates: [Path=/api/register/**]
    filters: [ApiKeyAuth, Logging, RequestRateLimiter]

  - id: subscriptions
    uri: ${backend.subscriptions.url}
    predicates: [Path=/api/subscriptions/**]
    filters: [ApiKeyAuth, Logging, RequestRateLimiter]

  - id: messages
    uri: ${backend.messages.url}
    predicates: [Path=/api/messages/**]
    filters: [ApiKeyAuth, Logging, RequestRateLimiter]

  # Public routes (no API key required)
  - id: stripe-webhook
    uri: ${backend.subscriptions.url}
    predicates: [Path=/webhooks/stripe]
    filters: [Logging]  # No ApiKeyAuth!

  - id: twilio-webhook
    uri: ${backend.messages.url}
    predicates: [Path=/webhooks/twilio/**]
    filters: [Logging]  # No ApiKeyAuth!

  - id: health-checks
    uri: ${backend.registration.url}
    predicates: [Path=/actuator/health]
    filters: [Logging]  # No ApiKeyAuth!
```

### 3. Configuration Profiles

**local** (`application-local.yml`)
- Backend services: `localhost:8081/8082/8083`
- Redis: `localhost:6379`
- API keys: `local-dev-key-12345,test-key-67890`
- CORS: Allow `localhost:3000`, `localhost:8000`
- Logging: DEBUG level

**stage** (`application-stage.yml`)
- Backend services: Azure staging URLs
- Redis: Azure Cache for Redis (SSL enabled)
- API keys: From Azure Key Vault
- CORS: `*.azurestaticapps.net`
- Logging: INFO level

**prod** (`application-prod.yml`)
- Backend services: Azure production URLs
- Redis: Azure Cache for Redis (SSL enabled)
- API keys: From Azure Key Vault
- CORS: `wordsinseasonapp.com` only
- Logging: WARN level (less verbose)

## Common Tasks

### Adding a New Route

1. Update `application.yml`:
```yaml
- id: new-service
  uri: ${backend.newservice.url}
  predicates:
    - Path=/api/newservice/**
  filters:
    - name: ApiKeyAuth  # If auth required
    - name: Logging
    - name: RequestRateLimiter
      args:
        redis-rate-limiter.replenishRate: 10
        redis-rate-limiter.burstCapacity: 20
        key-resolver: "#{@apiKeyResolver}"
```

2. Add backend URL property:
```yaml
backend:
  newservice:
    url: http://localhost:8084
```

3. Test the route:
```bash
curl -X GET http://localhost:8080/api/newservice/test \
  -H "X-API-Key: local-dev-key-12345"
```

### Updating Rate Limits

Edit the route filter configuration:
```yaml
filters:
  - name: RequestRateLimiter
    args:
      redis-rate-limiter.replenishRate: 20  # requests per second
      redis-rate-limiter.burstCapacity: 40  # max burst
```

### Adding CORS Origins

Update `CorsConfig.java` or `cors.allowed-origins` property:
```yaml
cors:
  allowed-origins: https://wordsinseasonapp.com,https://app.wordsinseasonapp.com
```

### Rotating API Keys

1. Generate new key:
```bash
openssl rand -base64 32
```

2. Add to existing keys (don't remove old yet):
```yaml
api:
  keys: old-key-1,old-key-2,new-key-3
```

3. Update clients to use new key

4. After 30 days, remove old keys

## Testing

### Unit Testing Pattern

```java
@Test
void shouldAllowRequestWithValidApiKey() {
    // Arrange
    MockServerHttpRequest request = MockServerHttpRequest
        .get("/api/test")
        .header("X-API-Key", "test-key-1")
        .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    // Act
    var filter = filterFactory.apply(new Config());
    var result = filter.filter(exchange, chain);

    // Assert
    StepVerifier.create(result).verifyComplete();
    verify(chain).filter(exchange);
}
```

### Integration Testing

```bash
# Start Redis
docker run -d -p 6379:6379 redis:7-alpine

# Start gateway
./gradlew runLocal

# Test protected endpoint
curl -X POST http://localhost:8080/api/register/phone \
  -H "Content-Type: application/json" \
  -H "X-API-Key: local-dev-key-12345" \
  -d '{"phoneNumber": "+15551234567"}'

# Test without API key (should return 401)
curl -X POST http://localhost:8080/api/register/phone \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+15551234567"}'

# Test public endpoint (no API key needed)
curl http://localhost:8080/actuator/health
```

## Deployment

### Azure Deployment

```bash
# Build
./gradlew build

# Deploy to staging
az webapp deploy \
  --resource-group wis-platform-prod \
  --name wis-api-gateway-stage \
  --src-path build/libs/wis-api-gateway-1.0.0.jar \
  --type jar

# Deploy to production
az webapp deploy \
  --resource-group wis-platform-prod \
  --name wis-api-gateway \
  --src-path build/libs/wis-api-gateway-1.0.0.jar \
  --type jar
```

### Environment Variables (Azure App Service)

```bash
az webapp config appsettings set \
  --resource-group wis-platform-prod \
  --name wis-api-gateway \
  --settings \
  SPRING_PROFILES_ACTIVE=prod \
  API_KEYS="@Microsoft.KeyVault(SecretUri=https://wis-keyvault.vault.azure.net/secrets/api-gateway-keys/)" \
  REDIS_HOST="@Microsoft.KeyVault(SecretUri=https://wis-keyvault.vault.azure.net/secrets/redis-host/)" \
  REDIS_PASSWORD="@Microsoft.KeyVault(SecretUri=https://wis-keyvault.vault.azure.net/secrets/redis-password/)" \
  WIS_REGISTRATION_URL="https://wis-registration.azurewebsites.net" \
  WIS_SUBSCRIPTIONS_URL="https://wis-subscriptions.azurewebsites.net" \
  WIS_MESSAGES_URL="https://wis-message-handler.azurewebsites.net"
```

## Troubleshooting

### Redis Connection Failed

```bash
# Test Redis locally
redis-cli -h localhost -p 6379 ping

# Test Azure Redis
redis-cli -h <redis-host> -p 6380 --tls -a <password> ping

# Check Redis config
grep -A 5 "spring.data.redis" src/main/resources/application.yml
```

### API Key Not Working

1. Check API keys configured:
```bash
echo $API_KEYS
```

2. Verify header name is exactly `X-API-Key`

3. Check logs for validation errors:
```bash
az webapp log tail --resource-group wis-platform-prod --name wis-api-gateway
```

### Route Not Found

1. Verify route configuration in `application.yml`
2. Check path predicate matches request path
3. Test path matching:
```bash
curl -v http://localhost:8080/api/register/phone
```

### Rate Limiting Not Working

1. Verify Redis is running and connected
2. Check rate limiter configuration in route
3. Test rate limit:
```bash
for i in {1..15}; do curl http://localhost:8080/api/test -H "X-API-Key: test-key"; done
```

## Best Practices

1. **Never log full API keys** - Always mask them
2. **Use Key Vault for secrets** - No secrets in code/config
3. **Test locally first** - Use `./gradlew runLocal`
4. **Monitor rate limits** - Adjust based on usage
5. **Keep routes simple** - Complex logic belongs in services
6. **Document route changes** - Update README when adding routes
7. **Use health checks** - Monitor all downstream services
8. **Enable CORS carefully** - Only allow trusted origins

## Contact

For questions or issues with the API Gateway:
- Check this file first
- Review TODO.md for known issues
- Check README.md for setup instructions
- Create an issue in the repository
