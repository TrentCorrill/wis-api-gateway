# Deployment Guide - Quick Reference

## Prerequisites

- [x] Azure CLI installed and logged in
- [x] Java 17 installed
- [x] Gradle 8.5+ installed (or use wrapper)
- [x] Access to WIS Azure subscription
- [x] Existing Key Vault: `wis-keyvault`

## Quick Start - Deploy to Azure

### Option 1: Automated Setup (Recommended)

```bash
# Run the automated setup script
./scripts/setup-azure.sh

# Choose environment when prompted:
#   - staging: Deploy staging only
#   - production: Deploy production only
#   - both: Deploy both environments

# Build the application
./gradlew clean build

# Deploy to staging
az webapp deploy \
  --resource-group wis-resources \
  --name wis-api-gateway-stage \
  --src-path build/libs/wis-api-gateway-1.0.0.jar \
  --type jar

# Or deploy to production
az webapp deploy \
  --resource-group wis-resources \
  --name wis-api-gateway \
  --src-path build/libs/wis-api-gateway-1.0.0.jar \
  --type jar
```

### Option 2: Manual Setup

See [Documentation/AZURE_DEPLOYMENT.md](Documentation/AZURE_DEPLOYMENT.md) for detailed manual setup instructions.

## CI/CD with GitHub Actions

### One-Time Setup

1. **Get Azure publish profiles:**

```bash
# Staging
az webapp deployment list-publishing-profiles \
  --resource-group wis-resources \
  --name wis-api-gateway-stage \
  --xml > stage-profile.xml

# Production
az webapp deployment list-publishing-profiles \
  --resource-group wis-resources \
  --name wis-api-gateway \
  --xml > prod-profile.xml
```

2. **Add GitHub secrets:**

Go to your repository → Settings → Secrets and variables → Actions:

- `AZURE_WEBAPP_PUBLISH_PROFILE_STAGE`: Paste content of `stage-profile.xml`
- `AZURE_WEBAPP_PUBLISH_PROFILE_PROD`: Paste content of `prod-profile.xml`

3. **Delete local profile files** (contains sensitive data):

```bash
rm stage-profile.xml prod-profile.xml
```

### Automated Deployments

Once GitHub secrets are configured:

- **Push to `develop` branch** → Auto-deploys to staging
- **Push to `main` branch** → Auto-deploys to production

## Testing After Deployment

### Staging

```bash
# Health check
curl https://wis-api-gateway-stage.azurewebsites.net/actuator/health

# Home endpoint
curl https://wis-api-gateway-stage.azurewebsites.net/

# Test auth (should return 401)
curl https://wis-api-gateway-stage.azurewebsites.net/api/subscriptions/test

# Test with API key (replace with your key)
curl -H "X-API-Key: YOUR_API_KEY_HERE" \
  https://wis-api-gateway-stage.azurewebsites.net/api/subscriptions/test
```

### Production

```bash
# Health check
curl https://wis-api-gateway.azurewebsites.net/actuator/health

# Home endpoint
curl https://wis-api-gateway.azurewebsites.net/

# Test auth (should return 401)
curl https://wis-api-gateway.azurewebsites.net/api/subscriptions/test

# Test with API key
curl -H "X-API-Key: YOUR_API_KEY_HERE" \
  https://wis-api-gateway.azurewebsites.net/api/subscriptions/test
```

## Get API Keys

API keys are stored in Azure Key Vault:

```bash
# Get staging API keys
az keyvault secret show \
  --vault-name wis-keyvault \
  --name API-GATEWAY-KEYS-STAGING \
  --query value -o tsv

# Get production API keys
az keyvault secret show \
  --vault-name wis-keyvault \
  --name API-GATEWAY-KEYS-PROD \
  --query value -o tsv
```

The keys are comma-separated: `frontend-key,mobile-key,admin-key`

## View Logs

```bash
# Staging logs (real-time)
az webapp log tail --resource-group wis-resources --name wis-api-gateway-stage

# Production logs (real-time)
az webapp log tail --resource-group wis-resources --name wis-api-gateway

# Download logs
az webapp log download \
  --resource-group wis-resources \
  --name wis-api-gateway \
  --log-file gateway-logs.zip
```

## Common Tasks

### Restart App Service

```bash
# Staging
az webapp restart --resource-group wis-resources --name wis-api-gateway-stage

# Production
az webapp restart --resource-group wis-resources --name wis-api-gateway
```

### Update Environment Variables

```bash
# Example: Update CORS origins
az webapp config appsettings set \
  --resource-group wis-resources \
  --name wis-api-gateway \
  --settings CORS_ALLOWED_ORIGINS="https://wordsinseason.com,https://www.wordsinseason.com"
```

### Scale App Service

```bash
# Scale up to S2 (more CPU/RAM)
az appservice plan update \
  --resource-group wis-resources \
  --name wis-plan-prod \
  --sku S2

# Scale out (add instances)
az appservice plan update \
  --resource-group wis-resources \
  --name wis-plan-prod \
  --number-of-workers 2
```

### Rotate API Keys

```bash
# Generate new keys
NEW_KEYS=$(openssl rand -hex 32),$(openssl rand -hex 32),$(openssl rand -hex 32)

# Update Key Vault
az keyvault secret set \
  --vault-name wis-keyvault \
  --name API-GATEWAY-KEYS-PROD \
  --value "$NEW_KEYS"

# Restart app to pick up new keys
az webapp restart --resource-group wis-resources --name wis-api-gateway

# Update frontend with new key
echo "New frontend API key: $(echo $NEW_KEYS | cut -d',' -f1)"
```

## Troubleshooting

### App won't start

```bash
# Check logs
az webapp log tail --resource-group wis-resources --name wis-api-gateway-stage

# Common issues:
# 1. Key Vault access denied → Check managed identity permissions
# 2. Redis connection failed → Check Redis firewall rules
# 3. Backend services unreachable → Check URLs in app settings
```

### 502 Bad Gateway

Usually means the app crashed or didn't start properly:

```bash
# Check logs
az webapp log tail --resource-group wis-resources --name wis-api-gateway-stage

# Restart the app
az webapp restart --resource-group wis-resources --name wis-api-gateway-stage
```

### Rate limiting not working

```bash
# Check Redis connection
az redis show --resource-group wis-resources --name wis-redis-stage --query provisioningState

# Test Redis connectivity
az redis show --resource-group wis-resources --name wis-redis-stage --query hostName
```

## Rollback

If a deployment causes issues:

```bash
# Re-deploy previous version
az webapp deploy \
  --resource-group wis-resources \
  --name wis-api-gateway \
  --src-path build/libs/wis-api-gateway-1.0.0-backup.jar \
  --type jar

# Or use GitHub Actions to re-deploy previous commit
# Just revert the commit and push to trigger deployment
```

## Monitoring

### Application Insights

View in Azure Portal:
- Go to Resource Group → wis-appinsights
- View dashboards for request rate, response times, errors

### Health Check

```bash
# Quick health check script
#!/bin/bash
health=$(curl -s https://wis-api-gateway.azurewebsites.net/actuator/health | jq -r .status)
if [ "$health" == "UP" ]; then
  echo "✅ API Gateway is healthy"
else
  echo "❌ API Gateway is down: $health"
fi
```

## Cost Management

View current costs:

```bash
# View cost analysis
az consumption usage list \
  --start-date 2025-10-01 \
  --end-date 2025-10-31 \
  --query "[?contains(instanceName, 'wis-api-gateway') || contains(instanceName, 'wis-redis')]"
```

Expected monthly costs:
- **Staging**: ~$30/month (B1 + Redis Basic)
- **Production**: ~$156/month (S1 + Redis Standard + App Insights)

## Next Steps

After deploying the API Gateway:

1. **Update frontend** to use gateway URL and API key
2. **Update backend services** to accept API key authentication
3. **Update webhooks** (Stripe, Twilio) to use gateway URL
4. **Configure monitoring alerts** in Application Insights
5. **Set up custom domain** (api.wordsinseason.com)

## Support

For issues or questions:
- Check logs: `az webapp log tail`
- Review Application Insights
- Check Azure Portal for resource status
