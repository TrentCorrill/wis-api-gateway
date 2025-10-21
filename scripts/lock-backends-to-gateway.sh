#!/bin/bash

# Lock backend services to only accept traffic from API Gateway
# This prevents direct access to backend services

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_info "Locking backend services to API Gateway IPs only..."
echo ""

# Staging API Gateway outbound IPs
STAGING_GATEWAY_IPS=(
    "104.208.34.190/32"
    "104.208.35.64/32"
    "104.208.36.234/32"
    "104.208.36.42/32"
    "104.208.37.158/32"
    "104.43.196.83/32"
    "104.43.201.174/32"
    "104.43.201.250/32"
    "104.43.202.37/32"
    "13.89.172.3/32"
    "20.83.2.253/32"
    "20.83.3.146/32"
    "20.83.3.184/32"
    "20.83.3.189/32"
    "20.83.3.230/32"
    "20.83.4.4/32"
    "20.83.5.146/32"
    "20.83.5.181/32"
    "20.83.5.2/32"
    "20.83.5.216/32"
    "20.83.5.28/32"
    "20.83.5.66/32"
)

# Production API Gateway outbound IPs
PROD_GATEWAY_IPS=(
    "168.61.146.106/32"
    "168.61.146.253/32"
    "168.61.150.120/32"
    "168.61.150.239/32"
    "168.61.159.114/32"
    "168.61.184.160/32"
    "168.61.185.197/32"
    "168.61.185.74/32"
    "168.61.191.135/32"
    "168.61.191.29/32"
    "20.221.42.179/32"
    "20.221.42.209/32"
    "20.221.42.41/32"
    "20.221.43.183/32"
    "20.221.43.249/32"
    "20.221.44.31/32"
    "52.158.167.213/32"
    "52.185.104.115/32"
    "52.185.104.153/32"
    "52.185.104.245/32"
    "52.185.104.66/32"
    "52.185.105.6/32"
)

# Function to lock down a backend service
lock_backend_service() {
    local RESOURCE_GROUP=$1
    local APP_NAME=$2
    local ENV_NAME=$3
    shift 3
    local GATEWAY_IPS=("$@")

    print_info "Configuring $ENV_NAME: $APP_NAME"

    # Remove default "Allow all" rule
    print_info "  Removing default 'Allow all' rule..."
    az webapp config access-restriction remove \
        --resource-group "$RESOURCE_GROUP" \
        --name "$APP_NAME" \
        --rule-name "Allow all" 2>/dev/null || true

    # Add API Gateway IPs
    local priority=100
    for ip in "${GATEWAY_IPS[@]}"; do
        print_info "  Adding API Gateway IP: $ip"
        az webapp config access-restriction add \
            --resource-group "$RESOURCE_GROUP" \
            --name "$APP_NAME" \
            --rule-name "APIGateway-$priority" \
            --action Allow \
            --ip-address "$ip" \
            --priority $priority > /dev/null
        ((priority++))
    done

    print_info "  ✓ $APP_NAME locked to API Gateway"
    echo ""
}

# Ask which environment
echo "Which environment(s) to lock down?"
echo "  1) Staging backend services only"
echo "  2) Production backend services only"
echo "  3) Both"
read -p "Choice (1-3): " choice

case $choice in
    1)
        print_info "Locking down STAGING backend services..."
        echo ""
        lock_backend_service "Staging" "wis-registration-stage" "Staging Registration" "${STAGING_GATEWAY_IPS[@]}"
        lock_backend_service "Staging" "wis-subscriptions-stage" "Staging Subscriptions" "${STAGING_GATEWAY_IPS[@]}"
        lock_backend_service "Staging" "wis-message-handler-stage" "Staging Messages" "${STAGING_GATEWAY_IPS[@]}"
        ;;
    2)
        print_info "Locking down PRODUCTION backend services..."
        echo ""
        lock_backend_service "Production" "wis-registration-prod" "Production Registration" "${PROD_GATEWAY_IPS[@]}"
        lock_backend_service "Production" "wis-subscriptions-prod" "Production Subscriptions" "${PROD_GATEWAY_IPS[@]}"
        lock_backend_service "Production" "wis-message-handler-prod" "Production Messages" "${PROD_GATEWAY_IPS[@]}"
        ;;
    3)
        print_info "Locking down ALL backend services..."
        echo ""
        lock_backend_service "Staging" "wis-registration-stage" "Staging Registration" "${STAGING_GATEWAY_IPS[@]}"
        lock_backend_service "Staging" "wis-subscriptions-stage" "Staging Subscriptions" "${STAGING_GATEWAY_IPS[@]}"
        lock_backend_service "Staging" "wis-message-handler-stage" "Staging Messages" "${STAGING_GATEWAY_IPS[@]}"
        lock_backend_service "Production" "wis-registration-prod" "Production Registration" "${PROD_GATEWAY_IPS[@]}"
        lock_backend_service "Production" "wis-subscriptions-prod" "Production Subscriptions" "${PROD_GATEWAY_IPS[@]}"
        lock_backend_service "Production" "wis-message-handler-prod" "Production Messages" "${PROD_GATEWAY_IPS[@]}"
        ;;
    *)
        print_error "Invalid choice"
        exit 1
        ;;
esac

print_info "========================================================="
print_info "Backend services now locked to API Gateway IPs only!"
print_info "========================================================="
echo ""
print_warning "Direct access to backend *.azurewebsites.net will now fail"
print_warning "All traffic must go through the API Gateway"
echo ""
print_info "Test with:"
echo "  curl https://wis-registration-stage.azurewebsites.net/actuator/health (should fail)"
echo "  curl -H 'X-API-Key: YOUR_KEY' https://api.wordsinseason.com/api/register/test (should work)"
