# WIS API Gateway

API Gateway for the Words in Season (WIS) platform. Provides unified entry point for all backend microservices with authentication, rate limiting, and routing.

## Features

- **Unified API Entry Point**: Single endpoint for all client requests
- **API Key Authentication**: X-API-Key header validation for all protected routes
- **Rate Limiting**: Redis-backed rate limiting per API key
- **CORS Handling**: Configurable CORS for web applications
- **Request Routing**: Intelligent routing to backend services
- **Health Checks**: Aggregated health monitoring of downstream services
- **Webhook Support**: Special handling for Stripe/Twilio webhooks (bypasses auth)
- **Logging & Monitoring**: Comprehensive request/response logging with Application Insights

## Tech Stack

- **Java 17**
- **Spring Boot 3.2.1**
- **Spring Cloud Gateway 2023.0.0**
- **Redis**: Rate limiting
- **Azure**: App Service, Key Vault, Application Insights
- **Gradle 8.5**

## Quick Start

### Prerequisites

- Java 17 or higher
- Docker (for local Redis)
- Gradle 8.5+ (or use wrapper)

### Run Locally

```bash
# Start Redis
docker run -d -p 6379:6379 redis:7-alpine

# Run application
./gradlew runLocal

# Test endpoint
curl -X GET http://localhost:8080/ \
  -H "X-API-Key: local-dev-key-12345"
```

The application will start on `http://localhost:8080`

## API Routes

| Path | Backend Service | Auth Required | Rate Limit |
|------|----------------|---------------|------------|
| `/api/register/**` | wis-registration | ✅ Yes | 10 req/min |
| `/api/subscriptions/**` | wis-subscriptions | ✅ Yes | 10 req/min |
| `/api/messages/**` | wis-message-handler | ✅ Yes | 10 req/min |
| `/webhooks/stripe` | wis-subscriptions | ❌ No | None |
| `/webhooks/twilio/**` | wis-message-handler | ❌ No | None |
| `/actuator/health` | Gateway | ❌ No | None |
| `/health/all` | All Services | ❌ No | None |

## Configuration

### Environment Variables

```bash
# API Keys (comma-separated for multiple keys)
API_KEYS=your-api-key-1,your-api-key-2

# Redis Configuration
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Backend Service URLs
WIS_REGISTRATION_URL=http://localhost:8081
WIS_SUBSCRIPTIONS_URL=http://localhost:8082
WIS_MESSAGES_URL=http://localhost:8083

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:3000,https://wordsinseason.com

# Application Insights (Azure)
APPINSIGHTS_INSTRUMENTATIONKEY=your-key
```

### Profiles

- **local**: Local development with localhost services
- **stage**: Azure staging environment
- **prod**: Azure production environment

```bash
# Run with specific profile
./gradlew bootRun --args='--spring.profiles.active=stage'
```

## Development

### Project Structure

```
src/
├── main/
│   ├── java/com/wis/apigateway/
│   │   ├── ApiGatewayApplication.java
│   │   ├── config/
│   │   │   ├── CorsConfig.java
│   │   │   └── RedisConfig.java
│   │   ├── filter/
│   │   │   ├── ApiKeyAuthGatewayFilterFactory.java
│   │   │   ├── ApiKeyResolver.java
│   │   │   └── LoggingGatewayFilterFactory.java
│   │   ├── controller/
│   │   │   ├── HealthController.java
│   │   │   └── HomeController.java
│   │   └── exception/
│   │       └── GlobalExceptionHandler.java
│   └── resources/
│       ├── application.yml
│       ├── application-local.yml
│       ├── application-stage.yml
│       └── application-prod.yml
└── test/
    └── java/com/wis/apigateway/
        └── filter/
            └── ApiKeyAuthFilterTests.java
```

### Build

```bash
# Clean build
./gradlew clean build

# Build without tests
./gradlew build -x test

# Run tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport
```

### Docker

```bash
# Build image
docker build -t wis-api-gateway:latest .

# Run with Docker Compose (includes Redis)
docker-compose up

# Stop services
docker-compose down
```

## Testing

### Unit Tests

```bash
./gradlew test
```

### Integration Testing

```bash
# Test with real backend services running
curl -X POST http://localhost:8080/api/register/phone \
  -H "Content-Type: application/json" \
  -H "X-API-Key: local-dev-key-12345" \
  -d '{"phoneNumber": "+15551234567"}'
```

### Test Without API Key (Should return 401)

```bash
curl -X POST http://localhost:8080/api/register/phone \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+15551234567"}'
```

## Azure Deployment

### Create Resources

```bash
# Set variables
RESOURCE_GROUP="wis-platform-prod"
GATEWAY_NAME="wis-api-gateway"

# Create Redis Cache
az redis create \
  --resource-group $RESOURCE_GROUP \
  --name wis-redis \
  --sku Basic \
  --vm-size c0

# Create App Service
az webapp create \
  --resource-group $RESOURCE_GROUP \
  --plan wis-app-plan \
  --name $GATEWAY_NAME \
  --runtime "JAVA:17-java17"

# Deploy
./gradlew build
az webapp deploy \
  --resource-group $RESOURCE_GROUP \
  --name $GATEWAY_NAME \
  --src-path build/libs/wis-api-gateway-1.0.0.jar \
  --type jar
```

### Configure Environment

Set environment variables in Azure App Service with Key Vault references:

```bash
az webapp config appsettings set \
  --resource-group $RESOURCE_GROUP \
  --name $GATEWAY_NAME \
  --settings \
  API_KEYS="@Microsoft.KeyVault(SecretUri=...)" \
  REDIS_HOST="@Microsoft.KeyVault(SecretUri=...)" \
  REDIS_PASSWORD="@Microsoft.KeyVault(SecretUri=...)"
```

## Monitoring

### Health Checks

```bash
# Gateway health
curl http://localhost:8080/actuator/health

# All services health
curl http://localhost:8080/health/all
```

### Metrics

```bash
# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Application metrics
curl http://localhost:8080/actuator/metrics
```

### Logs

```bash
# Azure App Service logs
az webapp log tail --resource-group wis-platform-prod --name wis-api-gateway
```

## Security

### API Key Management

```bash
# Generate secure API key
openssl rand -base64 32

# Store in Azure Key Vault
az keyvault secret set \
  --vault-name wis-keyvault \
  --name api-gateway-keys \
  --value "key1,key2,key3"
```

### Best Practices

- ✅ Store API keys in Azure Key Vault
- ✅ Rotate API keys quarterly
- ✅ Use different keys per environment
- ✅ Monitor for failed authentication attempts
- ✅ Enable rate limiting
- ✅ Use HTTPS only in production

## Troubleshooting

### Redis Connection Failed

```bash
# Test Redis connectivity
redis-cli -h localhost -p 6379 ping

# Check Redis password
echo $REDIS_PASSWORD
```

### API Key Not Working

Check that:
1. API key is configured in `API_KEYS` environment variable
2. Header name is exactly `X-API-Key`
3. No extra whitespace in key value
4. Key matches one in comma-separated list

### Service Not Reachable

```bash
# Test backend service health
curl https://wis-registration-stage.azurewebsites.net/actuator/health

# Check route configuration
grep -A 5 "registration:" src/main/resources/application.yml
```

## Documentation

- [API Gateway Implementation Guide](Documentation/API_GATEWAY.md)
- [Production Integration Plan](../Desktop/WIS-PRODUCTION-READY-INTEGRATION-PLAN.md)
- [TODO List](TODO.md)

## Contributing

1. Create feature branch: `git checkout -b feature/my-feature`
2. Make changes and test locally
3. Run tests: `./gradlew test`
4. Commit: `git commit -m "Description"`
5. Push: `git push origin feature/my-feature`
6. Create Pull Request

## License

Proprietary - Words In Season LLC

## Support

For issues or questions:
- Create an issue in this repository
- Contact: dev@wordsinseason.com
