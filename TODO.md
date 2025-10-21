# WIS API Gateway - TODO List

**Project Status:** 🟡 In Development
**Last Updated:** October 21, 2025

---

## Phase 1: Core Implementation (Current Phase)

### ✅ Completed
- [x] Create project structure
- [x] Configure Gradle build
- [x] Create README documentation
- [x] Create TODO list

### 🔄 In Progress
- [ ] Implement core gateway functionality

### ⬜ Todo

#### Java Implementation
- [ ] **Main Application** (`ApiGatewayApplication.java`)
  - [ ] Create main Spring Boot application class
  - [ ] Add @SpringBootApplication annotation
  - [ ] Test application starts successfully

#### Filters
- [ ] **API Key Auth Filter** (`ApiKeyAuthGatewayFilterFactory.java`)
  - [ ] Create filter factory class
  - [ ] Implement API key validation logic
  - [ ] Add API key masking for logs
  - [ ] Handle missing/invalid API keys (401 response)
  - [ ] Test with valid and invalid keys

- [ ] **API Key Resolver** (`ApiKeyResolver.java`)
  - [ ] Create key resolver for rate limiting
  - [ ] Extract API key from request header
  - [ ] Return key for rate limit bucket
  - [ ] Test resolver functionality

- [ ] **Logging Filter** (`LoggingGatewayFilterFactory.java`)
  - [ ] Create logging filter
  - [ ] Log request method, path, IP
  - [ ] Log response status and duration
  - [ ] Test log output

#### Configuration
- [ ] **CORS Config** (`CorsConfig.java`)
  - [ ] Create CORS configuration bean
  - [ ] Set allowed origins from properties
  - [ ] Set allowed methods (GET, POST, PUT, DELETE, OPTIONS)
  - [ ] Set allowed headers
  - [ ] Enable credentials
  - [ ] Test CORS preflight requests

- [ ] **Redis Config** (`RedisConfig.java`)
  - [ ] Create Redis configuration (uses auto-config)
  - [ ] Document Redis connection properties
  - [ ] Test Redis connectivity

#### Controllers
- [ ] **Health Controller** (`HealthController.java`)
  - [ ] Create aggregated health endpoint `/health/all`
  - [ ] Call all downstream service health checks
  - [ ] Return combined health status
  - [ ] Handle service failures gracefully
  - [ ] Test with services up and down

- [ ] **Home Controller** (`HomeController.java`)
  - [ ] Create home endpoint `/`
  - [ ] Return service information
  - [ ] Return links to documentation and health
  - [ ] Test endpoint

#### Exception Handling
- [ ] **Global Exception Handler** (`GlobalExceptionHandler.java`)
  - [ ] Implement ErrorWebExceptionHandler
  - [ ] Handle all uncaught exceptions
  - [ ] Return JSON error responses
  - [ ] Log errors with context
  - [ ] Test error scenarios

---

## Phase 2: Configuration Files

### Application Properties
- [ ] **application.yml** (Base configuration)
  - [ ] Configure server port (8080)
  - [ ] Define all gateway routes
    - [ ] Registration service route
    - [ ] Subscriptions service route
    - [ ] Messages service route
    - [ ] Stripe webhook route (no auth)
    - [ ] Twilio webhook route (no auth)
    - [ ] Health check route (no auth)
  - [ ] Configure rate limiting per route
  - [ ] Set Redis connection properties
  - [ ] Configure actuator endpoints
  - [ ] Set logging levels
  - [ ] Test configuration loads

- [ ] **application-local.yml** (Local development)
  - [ ] Set backend URLs to localhost
  - [ ] Configure local Redis
  - [ ] Set local API keys
  - [ ] Allow localhost CORS
  - [ ] Enable DEBUG logging
  - [ ] Test local profile

- [ ] **application-stage.yml** (Staging)
  - [ ] Set Azure staging service URLs
  - [ ] Configure Azure Redis with SSL
  - [ ] Reference Key Vault for API keys
  - [ ] Set staging CORS origins
  - [ ] Configure Application Insights
  - [ ] Test stage profile

- [ ] **application-prod.yml** (Production)
  - [ ] Set Azure production service URLs
  - [ ] Configure Azure Redis with SSL
  - [ ] Reference Key Vault for API keys
  - [ ] Set production CORS origins (strict)
  - [ ] Configure Application Insights
  - [ ] Set INFO logging only
  - [ ] Test prod profile

---

## Phase 3: Docker & Deployment

### Docker
- [ ] **Dockerfile**
  - [ ] Multi-stage build (Gradle → JRE)
  - [ ] Use Java 17 base image
  - [ ] Add non-root user
  - [ ] Copy JAR from build
  - [ ] Expose port 8080
  - [ ] Add health check
  - [ ] Test Docker build

- [ ] **docker-compose.yml**
  - [ ] Add Redis service
  - [ ] Add API Gateway service
  - [ ] Configure networking
  - [ ] Add health checks
  - [ ] Test with `docker-compose up`

### Gradle Wrapper
- [ ] Add Gradle wrapper files
  - [ ] `gradlew`
  - [ ] `gradlew.bat`
  - [ ] `gradle/wrapper/gradle-wrapper.jar`
  - [ ] `gradle/wrapper/gradle-wrapper.properties`

---

## Phase 4: Testing

### Unit Tests
- [ ] **ApiKeyAuthFilterTests**
  - [ ] Test with valid API key (should allow)
  - [ ] Test with invalid API key (should reject 401)
  - [ ] Test without API key (should reject 401)
  - [ ] Test with multiple configured keys
  - [ ] Verify chain.filter() called on success

- [ ] **ApiKeyResolverTests**
  - [ ] Test key extraction from header
  - [ ] Test with missing header (returns "anonymous")
  - [ ] Test resolver bean registration

- [ ] **ApplicationTests**
  - [ ] Test Spring context loads
  - [ ] Test all beans created
  - [ ] Test with local profile

### Integration Tests
- [ ] **Route Tests**
  - [ ] Test registration route forwards correctly
  - [ ] Test subscriptions route forwards correctly
  - [ ] Test messages route forwards correctly
  - [ ] Test webhook routes bypass auth
  - [ ] Test health routes public

- [ ] **Rate Limiting Tests**
  - [ ] Test rate limit enforced
  - [ ] Test burst capacity
  - [ ] Test rate limit per API key
  - [ ] Test rate limit reset

### Manual Testing
- [ ] Test locally with Docker Redis
- [ ] Test with backend services running
- [ ] Test CORS from frontend
- [ ] Test error responses
- [ ] Test health checks
- [ ] Load test with JMeter/k6

---

## Phase 5: Azure Deployment

### Azure Resources
- [ ] **Create Azure Cache for Redis**
  - [ ] Run `az redis create`
  - [ ] Get connection string
  - [ ] Store in Key Vault
  - [ ] Test connectivity

- [ ] **Create App Service**
  - [ ] Create or use existing App Service Plan
  - [ ] Create Web App for gateway
  - [ ] Configure Java 17 runtime
  - [ ] Enable managed identity
  - [ ] Test app service created

- [ ] **Configure Key Vault Access**
  - [ ] Grant managed identity access to Key Vault
  - [ ] Verify secret access
  - [ ] Test Key Vault integration

### Environment Configuration
- [ ] **Staging Environment**
  - [ ] Set `SPRING_PROFILES_ACTIVE=stage`
  - [ ] Configure API_KEYS from Key Vault
  - [ ] Configure REDIS_HOST from Key Vault
  - [ ] Configure REDIS_PASSWORD from Key Vault
  - [ ] Configure backend service URLs
  - [ ] Configure CORS origins
  - [ ] Configure Application Insights key
  - [ ] Test all environment variables

- [ ] **Production Environment**
  - [ ] Set `SPRING_PROFILES_ACTIVE=prod`
  - [ ] Configure API_KEYS from Key Vault
  - [ ] Configure REDIS_HOST from Key Vault
  - [ ] Configure REDIS_PASSWORD from Key Vault
  - [ ] Configure backend service URLs
  - [ ] Configure CORS origins (strict)
  - [ ] Configure Application Insights key
  - [ ] Test all environment variables

### Deployment
- [ ] **Build & Deploy to Staging**
  - [ ] Run `./gradlew build`
  - [ ] Deploy JAR to Azure
  - [ ] Verify deployment successful
  - [ ] Check application logs
  - [ ] Test health endpoint
  - [ ] Test API routes

- [ ] **Build & Deploy to Production**
  - [ ] Run `./gradlew build`
  - [ ] Deploy JAR to Azure
  - [ ] Verify deployment successful
  - [ ] Check application logs
  - [ ] Test health endpoint
  - [ ] Test API routes
  - [ ] Monitor for 48 hours

---

## Phase 6: CI/CD

### GitHub Actions
- [ ] **Create `.github/workflows/deploy.yml`**
  - [ ] Configure build job
  - [ ] Configure test job
  - [ ] Configure deploy-staging job (on develop branch)
  - [ ] Configure deploy-production job (on main branch)
  - [ ] Add GitHub secrets
  - [ ] Test workflow

### GitHub Secrets
- [ ] Add `AZURE_WEBAPP_PUBLISH_PROFILE_STAGE`
- [ ] Add `AZURE_WEBAPP_PUBLISH_PROFILE_PROD`
- [ ] Test secret access

---

## Phase 7: Monitoring & Observability

### Application Insights
- [ ] **Configure Dashboards**
  - [ ] Request volume by route
  - [ ] Response time (p50, p95, p99)
  - [ ] Error rate
  - [ ] Failed authentication attempts
  - [ ] Rate limit violations

- [ ] **Configure Alerts**
  - [ ] API Gateway down (health check fails)
  - [ ] High response time (p95 > 2s)
  - [ ] High error rate (>5%)
  - [ ] Failed auth spike (>100/5min)
  - [ ] Rate limit violations

### Logging
- [ ] Review log levels for production
- [ ] Ensure no sensitive data logged
- [ ] Set up log retention
- [ ] Test log aggregation in Azure

---

## Phase 8: Documentation

### Code Documentation
- [ ] Add JavaDoc to all public classes
- [ ] Add JavaDoc to all public methods
- [ ] Document configuration properties
- [ ] Add inline comments for complex logic

### Operational Documentation
- [ ] Create deployment runbook
- [ ] Create troubleshooting guide
- [ ] Document common issues
- [ ] Create rollback procedure

### API Documentation
- [ ] Add OpenAPI/Swagger documentation
- [ ] Document all routes
- [ ] Document authentication
- [ ] Document rate limits
- [ ] Publish Swagger UI

---

## Phase 9: Integration with Other Services

### Frontend Integration
- [ ] Update frontend `api-client.js` to use gateway URL
- [ ] Add X-API-Key header to all requests
- [ ] Generate frontend API key
- [ ] Store API key in Static Web App config
- [ ] Test registration flow end-to-end
- [ ] Test error handling

### Backend Service Updates
- [ ] Verify wis-registration has API key auth
- [ ] Verify wis-message-handler has API key auth
- [ ] Update Stripe webhook URL to gateway
- [ ] Update Twilio webhook URL to gateway
- [ ] Test webhooks through gateway

---

## Phase 10: Security Hardening

### Security Audit
- [ ] Run OWASP dependency check
- [ ] Run Snyk vulnerability scan
- [ ] Review CORS configuration
- [ ] Review rate limiting settings
- [ ] Test webhook signature validation
- [ ] Verify no secrets in code/config
- [ ] Test API key rotation

### Penetration Testing
- [ ] Test for SQL injection (N/A - no DB)
- [ ] Test for XSS vulnerabilities
- [ ] Test for CSRF vulnerabilities
- [ ] Test authentication bypass
- [ ] Test rate limiting bypass
- [ ] Document findings and fixes

---

## Phase 11: Performance Optimization

### Load Testing
- [ ] Create JMeter test plan
- [ ] Test with 100 concurrent users
- [ ] Test with 1000 concurrent users
- [ ] Test rate limiting under load
- [ ] Test Redis performance
- [ ] Identify bottlenecks

### Optimization
- [ ] Optimize Redis connection pooling
- [ ] Optimize route matching
- [ ] Optimize logging (async if needed)
- [ ] Review memory usage
- [ ] Review CPU usage
- [ ] Implement caching if beneficial

---

## Phase 12: Production Readiness Checklist

### Pre-Launch
- [ ] ✅ All code implemented and tested
- [ ] ✅ Unit tests passing (>80% coverage)
- [ ] ✅ Integration tests passing
- [ ] ✅ Deployed to staging successfully
- [ ] ✅ End-to-end testing complete
- [ ] ✅ Security audit complete
- [ ] ✅ Load testing complete
- [ ] ✅ Monitoring configured
- [ ] ✅ Alerts configured
- [ ] ✅ Documentation complete
- [ ] ✅ Runbook created
- [ ] ✅ Rollback procedure documented
- [ ] ✅ Team trained on operations

### Launch
- [ ] Deploy to production
- [ ] Update DNS/frontend to use gateway
- [ ] Monitor closely for 48 hours
- [ ] Fix any issues immediately
- [ ] Communicate status to stakeholders

### Post-Launch
- [ ] Review monitoring data
- [ ] Optimize based on real usage
- [ ] Gather feedback
- [ ] Plan next iteration

---

## Future Enhancements (Backlog)

### Nice to Have
- [ ] JWT token authentication (replace API keys)
- [ ] User-specific rate limiting
- [ ] Request/response caching
- [ ] Circuit breaker pattern
- [ ] Retry logic for failed requests
- [ ] Request transformation/enrichment
- [ ] Response compression
- [ ] API versioning support
- [ ] GraphQL gateway support
- [ ] WebSocket support

### Monitoring Enhancements
- [ ] Distributed tracing with Zipkin/Jaeger
- [ ] Custom metrics dashboards
- [ ] Real-time alerting to Slack/Teams
- [ ] Automated incident response
- [ ] Cost tracking and optimization

---

## Notes

### Current Blockers
- None

### Dependencies
- Redis must be running for rate limiting
- Backend services must be accessible
- Azure Key Vault access required for secrets

### Decisions Made
- Using Spring Cloud Gateway over Kong/APIM (cost, simplicity)
- API key authentication over JWT (current phase)
- Redis for rate limiting over in-memory (scalability)
- Azure App Service over AKS (simplicity)

### Questions/Clarifications Needed
- None currently

---

**Progress:** 4/150 tasks completed (3%)
**Estimated Completion:** 2-3 weeks for full implementation
