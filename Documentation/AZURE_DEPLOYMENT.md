# Azure Deployment Guide - WIS API Gateway

This guide covers deploying the API Gateway to Azure App Service with all required resources.

## Prerequisites

- Azure CLI installed and logged in (`az login`)
- Azure subscription with appropriate permissions
- Existing WIS resource group and Key Vault
- Existing backend services deployed (registration, subscriptions, messages)

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     Azure Resources                      │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  ┌──────────────┐      ┌─────────────────────────────┐  │
│  │ API Gateway  │◄─────┤ Azure Cache for Redis       │  │
│  │ App Service  │      │ (Rate Limiting)             │  │
│  └──────┬───────┘      └─────────────────────────────┘  │
│         │                                                │
│         │              ┌─────────────────────────────┐  │
│         └─────────────►│ Azure Key Vault             │  │
│                        │ (API Keys, Redis Password)  │  │
│                        └─────────────────────────────┘  │
│                                                           │
│  ┌──────────────┐      ┌─────────────────────────────┐  │
│  │ Application  │◄─────┤ Backend Services:           │  │
│  │ Insights     │      │ - wis-registration          │  │
│  └──────────────┘      │ - wis-subscriptions         │  │
│                        │ - wis-message-handler       │  │
│                        └─────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## Deployment Steps

### Step 1: Set Environment Variables

```bash
# Common variables
RESOURCE_GROUP="wis-resources"
LOCATION="eastus"
KEY_VAULT_NAME="wis-keyvault"

# Staging environment
REDIS_NAME_STAGE="wis-redis-stage"
APP_NAME_STAGE="wis-api-gateway-stage"
APP_SERVICE_PLAN_STAGE="wis-plan-stage"

# Production environment
REDIS_NAME_PROD="wis-redis-prod"
APP_NAME_PROD="wis-api-gateway"
APP_SERVICE_PLAN_PROD="wis-plan-prod"
```

### Step 2: Create Azure Cache for Redis

#### Staging Redis
```bash
az redis create \
  --resource-group $RESOURCE_GROUP \
  --name $REDIS_NAME_STAGE \
  --location $LOCATION \
  --sku Basic \
  --vm-size c0 \
  --enable-non-ssl-port false

# Get Redis hostname and password
REDIS_HOST_STAGE=$(az redis show \
  --resource-group $RESOURCE_GROUP \
  --name $REDIS_NAME_STAGE \
  --query "hostName" -o tsv)

REDIS_PASSWORD_STAGE=$(az redis list-keys \
  --resource-group $RESOURCE_GROUP \
  --name $REDIS_NAME_STAGE \
  --query "primaryKey" -o tsv)

# Store in Key Vault
az keyvault secret set \
  --vault-name $KEY_VAULT_NAME \
  --name "REDIS-HOST-STAGE" \
  --value $REDIS_HOST_STAGE

az keyvault secret set \
  --vault-name $KEY_VAULT_NAME \
  --name "REDIS-PASSWORD-STAGE" \
  --value $REDIS_PASSWORD_STAGE
```

#### Production Redis
```bash
az redis create \
  --resource-group $RESOURCE_GROUP \
  --name $REDIS_NAME_PROD \
  --location $LOCATION \
  --sku Standard \
  --vm-size c1 \
  --enable-non-ssl-port false

# Get Redis hostname and password
REDIS_HOST_PROD=$(az redis show \
  --resource-group $RESOURCE_GROUP \
  --name $REDIS_NAME_PROD \
  --query "hostName" -o tsv)

REDIS_PASSWORD_PROD=$(az redis list-keys \
  --resource-group $RESOURCE_GROUP \
  --name $REDIS_NAME_PROD \
  --query "primaryKey" -o tsv)

# Store in Key Vault
az keyvault secret set \
  --vault-name $KEY_VAULT_NAME \
  --name "REDIS-HOST-PROD" \
  --value $REDIS_HOST_PROD

az keyvault secret set \
  --vault-name $KEY_VAULT_NAME \
  --name "REDIS-PASSWORD-PROD" \
  --value $REDIS_PASSWORD_PROD
```

### Step 3: Generate and Store API Keys

```bash
# Generate secure API keys
API_KEY_FRONTEND=$(openssl rand -hex 32)
API_KEY_MOBILE=$(openssl rand -hex 32)
API_KEY_ADMIN=$(openssl rand -hex 32)

# Staging keys
az keyvault secret set \
  --vault-name $KEY_VAULT_NAME \
  --name "API-GATEWAY-KEYS-STAGE" \
  --value "$API_KEY_FRONTEND,$API_KEY_MOBILE,$API_KEY_ADMIN"

# Production keys (generate separate ones for production)
API_KEY_FRONTEND_PROD=$(openssl rand -hex 32)
API_KEY_MOBILE_PROD=$(openssl rand -hex 32)
API_KEY_ADMIN_PROD=$(openssl rand -hex 32)

az keyvault secret set \
  --vault-name $KEY_VAULT_NAME \
  --name "API-GATEWAY-KEYS-PROD" \
  --value "$API_KEY_FRONTEND_PROD,$API_KEY_MOBILE_PROD,$API_KEY_ADMIN_PROD"

# Save frontend API key for later use
echo "Frontend API Key (Staging): $API_KEY_FRONTEND"
echo "Frontend API Key (Production): $API_KEY_FRONTEND_PROD"
```

### Step 4: Create App Service Plan (if needed)

Check if App Service Plans exist, create if needed:

```bash
# Check existing plans
az appservice plan list --resource-group $RESOURCE_GROUP --output table

# Create staging plan if needed (Basic tier)
az appservice plan create \
  --resource-group $RESOURCE_GROUP \
  --name $APP_SERVICE_PLAN_STAGE \
  --location $LOCATION \
  --sku B1 \
  --is-linux

# Create production plan if needed (Standard tier for auto-scaling)
az appservice plan create \
  --resource-group $RESOURCE_GROUP \
  --name $APP_SERVICE_PLAN_PROD \
  --location $LOCATION \
  --sku S1 \
  --is-linux
```

### Step 5: Create App Service for API Gateway

#### Staging App Service
```bash
az webapp create \
  --resource-group $RESOURCE_GROUP \
  --plan $APP_SERVICE_PLAN_STAGE \
  --name $APP_NAME_STAGE \
  --runtime "JAVA:17-java17"

# Enable managed identity
az webapp identity assign \
  --resource-group $RESOURCE_GROUP \
  --name $APP_NAME_STAGE

# Get managed identity principal ID
IDENTITY_ID_STAGE=$(az webapp identity show \
  --resource-group $RESOURCE_GROUP \
  --name $APP_NAME_STAGE \
  --query principalId -o tsv)

# Grant Key Vault access
az keyvault set-policy \
  --name $KEY_VAULT_NAME \
  --object-id $IDENTITY_ID_STAGE \
  --secret-permissions get list
```

#### Production App Service
```bash
az webapp create \
  --resource-group $RESOURCE_GROUP \
  --plan $APP_SERVICE_PLAN_PROD \
  --name $APP_NAME_PROD \
  --runtime "JAVA:17-java17"

# Enable managed identity
az webapp identity assign \
  --resource-group $RESOURCE_GROUP \
  --name $APP_NAME_PROD

# Get managed identity principal ID
IDENTITY_ID_PROD=$(az webapp identity show \
  --resource-group $RESOURCE_GROUP \
  --name $APP_NAME_PROD \
  --query principalId -o tsv)

# Grant Key Vault access
az keyvault set-policy \
  --name $KEY_VAULT_NAME \
  --object-id $IDENTITY_ID_PROD \
  --secret-permissions get list
```

### Step 6: Configure App Settings

#### Staging Configuration
```bash
az webapp config appsettings set \
  --resource-group $RESOURCE_GROUP \
  --name $APP_NAME_STAGE \
  --settings \
    SPRING_PROFILES_ACTIVE="stage" \
    API_KEYS="@Microsoft.KeyVault(SecretUri=https://${KEY_VAULT_NAME}.vault.azure.net/secrets/API-GATEWAY-KEYS-STAGE/)" \
    REDIS_HOST="@Microsoft.KeyVault(SecretUri=https://${KEY_VAULT_NAME}.vault.azure.net/secrets/REDIS-HOST-STAGE/)" \
    REDIS_PASSWORD="@Microsoft.KeyVault(SecretUri=https://${KEY_VAULT_NAME}.vault.azure.net/secrets/REDIS-PASSWORD-STAGE/)" \
    WIS_REGISTRATION_URL="https://wis-registration-stage.azurewebsites.net" \
    WIS_SUBSCRIPTIONS_URL="https://wis-subscriptions-stage.azurewebsites.net" \
    WIS_MESSAGES_URL="https://wis-message-handler-stage.azurewebsites.net" \
    CORS_ALLOWED_ORIGINS="https://*.azurestaticapps.net,https://stage.wordsinseason.com" \
    APPINSIGHTS_INSTRUMENTATIONKEY="@Microsoft.KeyVault(SecretUri=https://${KEY_VAULT_NAME}.vault.azure.net/secrets/APPINSIGHTS-INSTRUMENTATIONKEY/)"
```

#### Production Configuration
```bash
az webapp config appsettings set \
  --resource-group $RESOURCE_GROUP \
  --name $APP_NAME_PROD \
  --settings \
    SPRING_PROFILES_ACTIVE="prod" \
    API_KEYS="@Microsoft.KeyVault(SecretUri=https://${KEY_VAULT_NAME}.vault.azure.net/secrets/API-GATEWAY-KEYS-PROD/)" \
    REDIS_HOST="@Microsoft.KeyVault(SecretUri=https://${KEY_VAULT_NAME}.vault.azure.net/secrets/REDIS-HOST-PROD/)" \
    REDIS_PASSWORD="@Microsoft.KeyVault(SecretUri=https://${KEY_VAULT_NAME}.vault.azure.net/secrets/REDIS-PASSWORD-PROD/)" \
    WIS_REGISTRATION_URL="https://wis-registration.azurewebsites.net" \
    WIS_SUBSCRIPTIONS_URL="https://wis-subscriptions.azurewebsites.net" \
    WIS_MESSAGES_URL="https://wis-message-handler.azurewebsites.net" \
    CORS_ALLOWED_ORIGINS="https://wordsinseason.com,https://www.wordsinseason.com" \
    APPINSIGHTS_INSTRUMENTATIONKEY="@Microsoft.KeyVault(SecretUri=https://${KEY_VAULT_NAME}.vault.azure.net/secrets/APPINSIGHTS-INSTRUMENTATIONKEY/)"
```

### Step 7: Build and Deploy

#### Local Build
```bash
# Navigate to project directory
cd /Users/trent/GitHub/wis-api-gateway

# Build JAR
./gradlew clean build

# JAR location: build/libs/wis-api-gateway-1.0.0.jar
```

#### Deploy to Staging
```bash
az webapp deploy \
  --resource-group $RESOURCE_GROUP \
  --name $APP_NAME_STAGE \
  --src-path build/libs/wis-api-gateway-1.0.0.jar \
  --type jar
```

#### Deploy to Production
```bash
az webapp deploy \
  --resource-group $RESOURCE_GROUP \
  --name $APP_NAME_PROD \
  --src-path build/libs/wis-api-gateway-1.0.0.jar \
  --type jar
```

### Step 8: Verify Deployment

#### Check Staging
```bash
# View logs
az webapp log tail --resource-group $RESOURCE_GROUP --name $APP_NAME_STAGE

# Test health endpoint
curl https://${APP_NAME_STAGE}.azurewebsites.net/actuator/health

# Test home endpoint
curl https://${APP_NAME_STAGE}.azurewebsites.net/
```

#### Check Production
```bash
# View logs
az webapp log tail --resource-group $RESOURCE_GROUP --name $APP_NAME_PROD

# Test health endpoint
curl https://${APP_NAME_PROD}.azurewebsites.net/actuator/health

# Test home endpoint
curl https://${APP_NAME_PROD}.azurewebsites.net/
```

### Step 9: Configure Custom Domain (Optional)

```bash
# Add custom domain
az webapp config hostname add \
  --resource-group $RESOURCE_GROUP \
  --webapp-name $APP_NAME_PROD \
  --hostname api.wordsinseason.com

# Enable HTTPS
az webapp config ssl bind \
  --resource-group $RESOURCE_GROUP \
  --name $APP_NAME_PROD \
  --certificate-thumbprint <cert-thumbprint> \
  --ssl-type SNI
```

## Testing

### Test API Key Authentication

```bash
# Should return 401 (no API key)
curl https://${APP_NAME_STAGE}.azurewebsites.net/api/register/test

# Should forward to backend (with valid key)
curl -H "X-API-Key: $API_KEY_FRONTEND" \
  https://${APP_NAME_STAGE}.azurewebsites.net/api/register/test
```

### Test Rate Limiting

```bash
# Make 25 requests rapidly (should get rate limited after 20)
for i in {1..25}; do
  curl -H "X-API-Key: $API_KEY_FRONTEND" \
    https://${APP_NAME_STAGE}.azurewebsites.net/api/subscriptions/test
  echo " - Request $i"
done
```

## Monitoring

### View Application Insights

```bash
# Get Application Insights resource
az monitor app-insights component show \
  --resource-group $RESOURCE_GROUP \
  --app wis-appinsights

# View in portal
https://portal.azure.com/#@yourtenant/resource/subscriptions/.../insights
```

### View Metrics

- Request rate
- Response time (P50, P95, P99)
- Error rate
- Failed authentication attempts
- Rate limit violations

## Troubleshooting

### App won't start
```bash
# Check logs
az webapp log tail --resource-group $RESOURCE_GROUP --name $APP_NAME_STAGE

# Common issues:
# - Key Vault access denied (check managed identity)
# - Redis connection failed (check firewall rules)
# - Backend services unreachable (check URLs)
```

### Key Vault secrets not loading
```bash
# Verify managed identity has access
az keyvault show --name $KEY_VAULT_NAME --query properties.accessPolicies

# Test secret access
az keyvault secret show --vault-name $KEY_VAULT_NAME --name REDIS-HOST-STAGE
```

### Redis connection timeout
```bash
# Check Redis firewall rules
az redis firewall-rules list \
  --resource-group $RESOURCE_GROUP \
  --name $REDIS_NAME_STAGE

# Allow Azure services
az redis firewall-rules create \
  --resource-group $RESOURCE_GROUP \
  --name $REDIS_NAME_STAGE \
  --rule-name AllowAzure \
  --start-ip 0.0.0.0 \
  --end-ip 0.0.0.0
```

## Cost Estimation

### Staging Environment
- App Service (B1): ~$13/month
- Redis (Basic C0): ~$17/month
- **Total: ~$30/month**

### Production Environment
- App Service (S1): ~$70/month
- Redis (Standard C1): ~$76/month
- Application Insights: ~$10/month (first 5GB free)
- **Total: ~$156/month**

## Rollback Procedure

```bash
# List deployment slots
az webapp deployment list-publishing-profiles \
  --resource-group $RESOURCE_GROUP \
  --name $APP_NAME_PROD

# Swap slots (if using deployment slots)
az webapp deployment slot swap \
  --resource-group $RESOURCE_GROUP \
  --name $APP_NAME_PROD \
  --slot staging \
  --target-slot production

# Or re-deploy previous version
az webapp deploy \
  --resource-group $RESOURCE_GROUP \
  --name $APP_NAME_PROD \
  --src-path build/libs/wis-api-gateway-1.0.0-previous.jar \
  --type jar
```

## Next Steps

1. Set up CI/CD with GitHub Actions
2. Configure monitoring alerts
3. Update frontend to use API Gateway
4. Add authentication to backend services
5. Update webhook URLs (Stripe, Twilio)
