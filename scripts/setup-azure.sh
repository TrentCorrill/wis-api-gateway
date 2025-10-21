#!/bin/bash

# WIS API Gateway - Azure Setup Script
# This script creates all necessary Azure resources for the API Gateway

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Check if Azure CLI is installed
if ! command -v az &> /dev/null; then
    print_error "Azure CLI is not installed. Please install it first:"
    print_error "https://docs.microsoft.com/en-us/cli/azure/install-azure-cli"
    exit 1
fi

# Check if logged in
if ! az account show &> /dev/null; then
    print_error "Not logged in to Azure. Please run: az login"
    exit 1
fi

print_info "Starting Azure setup for WIS API Gateway..."

# Prompt for environment
echo ""
read -p "Which environment to set up? (staging/production/both): " ENVIRONMENT
echo ""

# Common variables
RESOURCE_GROUP="wis-resources"
LOCATION="eastus"
KEY_VAULT_NAME="wis-keyvault"

# Function to create Redis instance
create_redis() {
    local ENV=$1
    local REDIS_NAME=$2
    local SKU=$3
    local VM_SIZE=$4

    print_info "Creating Redis Cache for $ENV environment..."

    # Check if Redis already exists
    if az redis show --resource-group $RESOURCE_GROUP --name $REDIS_NAME &> /dev/null; then
        print_warning "Redis $REDIS_NAME already exists, skipping creation"
    else
        az redis create \
          --resource-group $RESOURCE_GROUP \
          --name $REDIS_NAME \
          --location $LOCATION \
          --sku $SKU \
          --vm-size $VM_SIZE \
          --enable-non-ssl-port false

        print_info "Waiting for Redis to be ready..."
        sleep 60
    fi

    # Get Redis details
    REDIS_HOST=$(az redis show \
      --resource-group $RESOURCE_GROUP \
      --name $REDIS_NAME \
      --query "hostName" -o tsv)

    REDIS_PASSWORD=$(az redis list-keys \
      --resource-group $RESOURCE_GROUP \
      --name $REDIS_NAME \
      --query "primaryKey" -o tsv)

    # Store in Key Vault
    print_info "Storing Redis credentials in Key Vault..."

    az keyvault secret set \
      --vault-name $KEY_VAULT_NAME \
      --name "REDIS-HOST-${ENV^^}" \
      --value $REDIS_HOST > /dev/null

    az keyvault secret set \
      --vault-name $KEY_VAULT_NAME \
      --name "REDIS-PASSWORD-${ENV^^}" \
      --value $REDIS_PASSWORD > /dev/null

    print_info "✓ Redis Cache for $ENV created: $REDIS_HOST"
}

# Function to generate and store API keys
generate_api_keys() {
    local ENV=$1

    print_info "Generating API keys for $ENV environment..."

    # Check if openssl is available
    if ! command -v openssl &> /dev/null; then
        print_error "openssl not found. Please install it to generate secure keys."
        exit 1
    fi

    # Generate three API keys
    API_KEY_FRONTEND=$(openssl rand -hex 32)
    API_KEY_MOBILE=$(openssl rand -hex 32)
    API_KEY_ADMIN=$(openssl rand -hex 32)

    # Combine keys
    API_KEYS="$API_KEY_FRONTEND,$API_KEY_MOBILE,$API_KEY_ADMIN"

    # Store in Key Vault
    az keyvault secret set \
      --vault-name $KEY_VAULT_NAME \
      --name "API-GATEWAY-KEYS-${ENV^^}" \
      --value "$API_KEYS" > /dev/null

    print_info "✓ API keys generated and stored"
    echo ""
    print_info "Frontend API Key ($ENV): $API_KEY_FRONTEND"
    print_warning "Save this key - you'll need it for frontend configuration!"
    echo ""
}

# Function to create App Service
create_app_service() {
    local ENV=$1
    local APP_NAME=$2
    local APP_PLAN=$3
    local SKU=$4

    print_info "Creating App Service for $ENV environment..."

    # Create App Service Plan if it doesn't exist
    if ! az appservice plan show --resource-group $RESOURCE_GROUP --name $APP_PLAN &> /dev/null; then
        print_info "Creating App Service Plan: $APP_PLAN"
        az appservice plan create \
          --resource-group $RESOURCE_GROUP \
          --name $APP_PLAN \
          --location $LOCATION \
          --sku $SKU \
          --is-linux > /dev/null
    else
        print_warning "App Service Plan $APP_PLAN already exists"
    fi

    # Create Web App if it doesn't exist
    if ! az webapp show --resource-group $RESOURCE_GROUP --name $APP_NAME &> /dev/null; then
        print_info "Creating Web App: $APP_NAME"
        az webapp create \
          --resource-group $RESOURCE_GROUP \
          --plan $APP_PLAN \
          --name $APP_NAME \
          --runtime "JAVA:17-java17" > /dev/null
    else
        print_warning "Web App $APP_NAME already exists"
    fi

    # Enable managed identity
    print_info "Enabling managed identity..."
    az webapp identity assign \
      --resource-group $RESOURCE_GROUP \
      --name $APP_NAME > /dev/null

    # Get managed identity
    IDENTITY_ID=$(az webapp identity show \
      --resource-group $RESOURCE_GROUP \
      --name $APP_NAME \
      --query principalId -o tsv)

    # Grant Key Vault access
    print_info "Granting Key Vault access to managed identity..."
    az keyvault set-policy \
      --name $KEY_VAULT_NAME \
      --object-id $IDENTITY_ID \
      --secret-permissions get list > /dev/null

    print_info "✓ App Service created: $APP_NAME"
}

# Function to configure app settings
configure_app_settings() {
    local ENV=$1
    local APP_NAME=$2
    local REG_URL=$3
    local SUB_URL=$4
    local MSG_URL=$5
    local CORS=$6

    print_info "Configuring app settings for $ENV environment..."

    az webapp config appsettings set \
      --resource-group $RESOURCE_GROUP \
      --name $APP_NAME \
      --settings \
        SPRING_PROFILES_ACTIVE="$ENV" \
        API_KEYS="@Microsoft.KeyVault(SecretUri=https://${KEY_VAULT_NAME}.vault.azure.net/secrets/API-GATEWAY-KEYS-${ENV^^}/)" \
        REDIS_HOST="@Microsoft.KeyVault(SecretUri=https://${KEY_VAULT_NAME}.vault.azure.net/secrets/REDIS-HOST-${ENV^^}/)" \
        REDIS_PASSWORD="@Microsoft.KeyVault(SecretUri=https://${KEY_VAULT_NAME}.vault.azure.net/secrets/REDIS-PASSWORD-${ENV^^}/)" \
        WIS_REGISTRATION_URL="$REG_URL" \
        WIS_SUBSCRIPTIONS_URL="$SUB_URL" \
        WIS_MESSAGES_URL="$MSG_URL" \
        CORS_ALLOWED_ORIGINS="$CORS" \
        APPINSIGHTS_INSTRUMENTATIONKEY="@Microsoft.KeyVault(SecretUri=https://${KEY_VAULT_NAME}.vault.azure.net/secrets/APPINSIGHTS-INSTRUMENTATIONKEY/)" \
        > /dev/null

    print_info "✓ App settings configured"
}

# Setup staging environment
setup_staging() {
    print_info "=== Setting up STAGING environment ==="
    echo ""

    create_redis "staging" "wis-redis-stage" "Basic" "c0"
    generate_api_keys "staging"
    create_app_service "staging" "wis-api-gateway-stage" "wis-plan-stage" "B1"
    configure_app_settings \
      "stage" \
      "wis-api-gateway-stage" \
      "https://wis-registration-stage.azurewebsites.net" \
      "https://wis-subscriptions-stage.azurewebsites.net" \
      "https://wis-message-handler-stage.azurewebsites.net" \
      "https://*.azurestaticapps.net,https://stage.wordsinseason.com"

    print_info "✓ Staging environment setup complete!"
    echo ""
    print_info "Staging URL: https://wis-api-gateway-stage.azurewebsites.net"
    echo ""
}

# Setup production environment
setup_production() {
    print_info "=== Setting up PRODUCTION environment ==="
    echo ""

    create_redis "production" "wis-redis-prod" "Standard" "c1"
    generate_api_keys "production"
    create_app_service "production" "wis-api-gateway" "wis-plan-prod" "S1"
    configure_app_settings \
      "prod" \
      "wis-api-gateway" \
      "https://wis-registration.azurewebsites.net" \
      "https://wis-subscriptions.azurewebsites.net" \
      "https://wis-message-handler.azurewebsites.net" \
      "https://wordsinseason.com,https://www.wordsinseason.com"

    print_info "✓ Production environment setup complete!"
    echo ""
    print_info "Production URL: https://wis-api-gateway.azurewebsites.net"
    echo ""
}

# Execute setup based on user input
case $ENVIRONMENT in
    staging)
        setup_staging
        ;;
    production)
        setup_production
        ;;
    both)
        setup_staging
        echo ""
        echo "=================================================="
        echo ""
        setup_production
        ;;
    *)
        print_error "Invalid environment: $ENVIRONMENT"
        print_error "Please choose: staging, production, or both"
        exit 1
        ;;
esac

print_info "=================================================="
print_info "Azure setup complete!"
print_info "=================================================="
echo ""
print_info "Next steps:"
echo "  1. Build the application: ./gradlew clean build"
echo "  2. Deploy using Azure CLI or GitHub Actions"
echo "  3. Test the health endpoint: curl https://<app-name>.azurewebsites.net/actuator/health"
echo "  4. Configure GitHub secrets for CI/CD"
echo ""
print_warning "Remember to save the API keys shown above!"
