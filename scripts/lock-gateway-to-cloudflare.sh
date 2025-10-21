#!/bin/bash

# Lock API Gateway to only accept traffic from Cloudflare
# This script adds IP restrictions to allow only Cloudflare IP ranges

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

print_info "Locking API Gateway to Cloudflare IPs only..."
echo ""

# Cloudflare IPv4 ranges (verify at https://www.cloudflare.com/ips/)
CLOUDFLARE_IPS=(
    "173.245.48.0/20"
    "103.21.244.0/22"
    "103.22.200.0/22"
    "103.31.4.0/22"
    "141.101.64.0/18"
    "108.162.192.0/18"
    "190.93.240.0/20"
    "188.114.96.0/20"
    "197.234.240.0/22"
    "198.41.128.0/17"
    "162.158.0.0/15"
    "104.16.0.0/13"
    "104.24.0.0/14"
    "172.64.0.0/13"
    "131.0.72.0/22"
)

# Function to lock down an app service
lock_app_service() {
    local RESOURCE_GROUP=$1
    local APP_NAME=$2
    local ENV_NAME=$3

    print_info "Configuring $ENV_NAME environment: $APP_NAME"

    # Remove default "Allow all" rule
    print_info "  Removing default 'Allow all' rule..."
    az webapp config access-restriction remove \
        --resource-group "$RESOURCE_GROUP" \
        --name "$APP_NAME" \
        --rule-name "Allow all" 2>/dev/null || true

    # Add Cloudflare IP ranges
    local priority=100
    for ip in "${CLOUDFLARE_IPS[@]}"; do
        print_info "  Adding Cloudflare range: $ip"
        az webapp config access-restriction add \
            --resource-group "$RESOURCE_GROUP" \
            --name "$APP_NAME" \
            --rule-name "Cloudflare-$priority" \
            --action Allow \
            --ip-address "$ip" \
            --priority $priority > /dev/null
        ((priority++))
    done

    print_info "  ✓ $APP_NAME locked to Cloudflare IPs"
    echo ""
}

# Ask which environment
echo "Which environment(s) to lock down?"
echo "  1) Staging only"
echo "  2) Production only"
echo "  3) Both"
read -p "Choice (1-3): " choice

case $choice in
    1)
        lock_app_service "Staging" "wis-api-gateway-stage" "Staging"
        ;;
    2)
        lock_app_service "Production" "wis-api-gateway" "Production"
        ;;
    3)
        lock_app_service "Staging" "wis-api-gateway-stage" "Staging"
        lock_app_service "Production" "wis-api-gateway" "Production"
        ;;
    *)
        print_error "Invalid choice"
        exit 1
        ;;
esac

print_info "=================================================="
print_info "API Gateway(s) now locked to Cloudflare IPs only!"
print_info "=================================================="
echo ""
print_warning "Direct access to *.azurewebsites.net will now fail"
print_warning "All traffic must go through api.wordsinseason.com"
echo ""
print_info "Test with:"
echo "  curl https://api.wordsinseason.com/actuator/health (should work)"
echo "  curl https://wis-api-gateway.azurewebsites.net/actuator/health (should fail)"
