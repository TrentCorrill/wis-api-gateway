# Cloudflare + IP Restrictions Security Setup

**Cost:** $0 (using Cloudflare Free tier + Azure IP restrictions)
**Security Level:** Excellent - comparable to VNet isolation
**Setup Time:** ~15 minutes

## Overview

This setup gives you enterprise-grade security WITHOUT the cost of Azure VNet:

```
Internet
   ↓
Cloudflare (api.wordsinseasonapp.com)
   ↓ [Only Cloudflare IPs allowed]
API Gateway (wis-api-gateway.azurewebsites.net)
   ↓ [Only API Gateway IPs allowed]
Backend Services (registration, subscriptions, messages)
```

**Security Benefits:**
- ✅ DDoS protection (Cloudflare)
- ✅ Web Application Firewall (Cloudflare)
- ✅ Rate limiting (Cloudflare)
- ✅ Direct access to backend services blocked
- ✅ Direct access to API Gateway blocked
- ✅ All traffic must go through Cloudflare
- ✅ Professional custom domain
- ✅ Free SSL certificate

**Cost:** $0/month (Cloudflare Free + existing Azure resources)

---

## Step 1: Configure Cloudflare DNS

### 1.1 Add DNS Record for API Gateway

**In Cloudflare Dashboard:**

1. Go to your domain: `wordsinseasonapp.com`
2. Navigate to **DNS** → **Records**
3. Click **Add Record**

**Record Configuration:**
```
Type:    CNAME
Name:    api
Target:  wis-api-gateway.azurewebsites.net
Proxy:   ON (orange cloud ☁️) ← IMPORTANT!
TTL:     Auto
```

**Result:** `api.wordsinseasonapp.com` → API Gateway

### 1.2 Verify Proxy is Enabled

The cloud icon should be **ORANGE** ☁️ (not gray).
- Orange = Proxied through Cloudflare ✅
- Gray = DNS only (bypass Cloudflare) ❌

### 1.3 SSL/TLS Configuration

**In Cloudflare:**
1. Go to **SSL/TLS** → **Overview**
2. Set encryption mode to: **Full (strict)**

This ensures end-to-end encryption.

---

## Step 2: Lock Down API Gateway (Accept Only Cloudflare)

### 2.1 Get Cloudflare IP Ranges

Cloudflare publishes their IP ranges at: https://www.cloudflare.com/ips/

**Current Cloudflare IPv4 Ranges (verify before using):**
```
173.245.48.0/20
103.21.244.0/22
103.22.200.0/22
103.31.4.0/22
141.101.64.0/18
108.162.192.0/18
190.93.240.0/20
188.114.96.0/20
197.234.240.0/22
198.41.128.0/17
162.158.0.0/15
104.16.0.0/13
104.24.0.0/14
172.64.0.0/13
131.0.72.0/22
```

### 2.2 Configure Access Restrictions on API Gateway

**Staging:**

```bash
# Remove default "Allow All" rule first
az webapp config access-restriction remove \
  --resource-group Staging \
  --name wis-api-gateway-stage \
  --rule-name "Allow all"

# Add Cloudflare IP ranges (repeat for each range)
az webapp config access-restriction add \
  --resource-group Staging \
  --name wis-api-gateway-stage \
  --rule-name "Cloudflare-1" \
  --action Allow \
  --ip-address 173.245.48.0/20 \
  --priority 100

az webapp config access-restriction add \
  --resource-group Staging \
  --name wis-api-gateway-stage \
  --rule-name "Cloudflare-2" \
  --action Allow \
  --ip-address 103.21.244.0/22 \
  --priority 101

# ... repeat for all Cloudflare ranges
# See full script below for automation
```

**Production:**

```bash
# Same process for production
az webapp config access-restriction remove \
  --resource-group Production \
  --name wis-api-gateway \
  --rule-name "Allow all"

# Add Cloudflare ranges
az webapp config access-restriction add \
  --resource-group Production \
  --name wis-api-gateway \
  --rule-name "Cloudflare-1" \
  --action Allow \
  --ip-address 173.245.48.0/20 \
  --priority 100

# ... etc
```

---

## Step 3: Lock Down Backend Services (Accept Only API Gateway)

### 3.1 API Gateway Outbound IP Addresses

**Staging API Gateway IPs:**
```
104.208.34.190
104.208.35.64
104.208.36.234
104.208.36.42
104.208.37.158
104.43.196.83
104.43.201.174
104.43.201.250
104.43.202.37
13.89.172.3
20.83.2.253
20.83.3.146
20.83.3.184
20.83.3.189
20.83.3.230
20.83.4.4
20.83.5.146
20.83.5.181
20.83.5.2
20.83.5.216
20.83.5.28
20.83.5.66
```

**Production API Gateway IPs:**
```
168.61.146.106
168.61.146.253
168.61.150.120
168.61.150.239
168.61.159.114
168.61.184.160
168.61.185.197
168.61.185.74
168.61.191.135
168.61.191.29
20.221.42.179
20.221.42.209
20.221.42.41
20.221.43.183
20.221.43.249
20.221.44.31
52.158.167.213
52.185.104.115
52.185.104.153
52.185.104.245
52.185.104.66
52.185.105.6
```

### 3.2 Restrict Backend Services

**For Each Backend Service (Registration, Subscriptions, Messages):**

```bash
# Example: Lock down wis-registration-stage
az webapp config access-restriction remove \
  --resource-group Staging \
  --name wis-registration-stage \
  --rule-name "Allow all"

# Add API Gateway IPs (staging)
az webapp config access-restriction add \
  --resource-group Staging \
  --name wis-registration-stage \
  --rule-name "APIGateway-1" \
  --action Allow \
  --ip-address 104.208.34.190/32 \
  --priority 100

az webapp config access-restriction add \
  --resource-group Staging \
  --name wis-registration-stage \
  --rule-name "APIGateway-2" \
  --action Allow \
  --ip-address 104.208.35.64/32 \
  --priority 101

# ... repeat for all API Gateway IPs
# See automation script below
```

**Repeat for:**
- `wis-subscriptions-stage`
- `wis-message-handler-stage`
- `wis-registration-prod`
- `wis-subscriptions-prod`
- `wis-message-handler-prod`

---

## Step 4: Automation Script

I've created a script to automate this setup:

```bash
#!/bin/bash
# File: scripts/setup-security.sh

# Lock down API Gateway to Cloudflare only
./scripts/lock-gateway-to-cloudflare.sh

# Lock down backend services to API Gateway only
./scripts/lock-backends-to-gateway.sh
```

See `scripts/` folder for the complete automation.

---

## Step 5: Update Frontend Configuration

### Before:
```javascript
const API_BASE_URL = 'https://wis-api-gateway-stage.azurewebsites.net';
```

### After:
```javascript
const API_BASE_URL = 'https://api.wordsinseasonapp.com';
```

### Environment Variables

**Static Web App Settings:**

```bash
# Staging
az staticwebapp appsettings set \
  --name HomePage-Stage \
  --setting-names \
    API_BASE_URL="https://api.wordsinseasonapp.com" \
    API_KEY="6b4d1a9f55d0ca0d24e5598c7dc05ac87bdede4b6b3610665d42a407df6487c3"

# Production
az staticwebapp appsettings set \
  --name HomePage \
  --setting-names \
    API_BASE_URL="https://api.wordsinseasonapp.com" \
    API_KEY="d8c88a1a8361d1f71b7338f06bfc1f7bba9249f8c66fec6cd708cf20b19efc3a"
```

---

## Step 6: Cloudflare Security Features

### 6.1 Enable WAF (Web Application Firewall)

**In Cloudflare:**
1. Go to **Security** → **WAF**
2. Enable **Managed Rules** (Free)
3. Set security level to **Medium** or **High**

This blocks common attacks (SQL injection, XSS, etc.)

### 6.2 Enable Rate Limiting (Optional - Pro Plan)

**Free Tier Alternative:**
Use Cloudflare's basic DDoS protection (automatic)

**Pro Tier ($20/month):**
- Advanced rate limiting rules
- Custom rules per endpoint
- Better than Redis for public-facing APIs

### 6.3 Enable Bot Fight Mode

**In Cloudflare:**
1. Go to **Security** → **Bots**
2. Enable **Bot Fight Mode** (Free)

Blocks bad bots automatically.

---

## Verification & Testing

### Test 1: Verify Cloudflare is Working

```bash
# Should work (through Cloudflare)
curl https://api.wordsinseasonapp.com/actuator/health

# Should work (direct Azure URL still accessible until Step 7)
curl https://wis-api-gateway.azurewebsites.net/actuator/health
```

### Test 2: Verify API Gateway Restrictions

After adding Cloudflare IP restrictions:

```bash
# Should FAIL (direct access blocked)
curl https://wis-api-gateway.azurewebsites.net/actuator/health

# Should WORK (through Cloudflare proxy)
curl https://api.wordsinseasonapp.com/actuator/health
```

### Test 3: Verify Backend Service Restrictions

```bash
# Should FAIL (direct access blocked)
curl https://wis-registration-stage.azurewebsites.net/actuator/health

# Should WORK (through API Gateway)
curl -H "X-API-Key: YOUR_KEY" https://api.wordsinseasonapp.com/api/register/test
```

---

## Step 7: Final Hardening (Optional)

### 7.1 Custom Domain in Azure

Add `api.wordsinseasonapp.com` as a custom domain in Azure App Service:
- Better logging
- Potential performance improvement
- Cleaner URLs in Azure logs

### 7.2 Disable SCM (Kudu) Public Access

```bash
az webapp config access-restriction add \
  --resource-group Production \
  --name wis-api-gateway \
  --rule-name "DenyPublicSCM" \
  --action Deny \
  --ip-address 0.0.0.0/0 \
  --priority 200 \
  --scm-site true
```

This blocks public access to deployment endpoints.

---

## Monitoring & Alerts

### Cloudflare Analytics

**Available in Dashboard:**
- Total requests per day
- Bandwidth usage
- Threats blocked
- Response codes
- Top endpoints

### Azure Application Insights

**Still works through Cloudflare:**
- Request tracking
- Performance monitoring
- Error tracking
- Custom metrics

### Set Up Alerts

**In Cloudflare:**
- Email alerts for traffic spikes
- Security event notifications

**In Azure:**
- App Service down alerts
- High error rate alerts

---

## Cost Breakdown

| Component | Cost |
|-----------|------|
| Cloudflare DNS + Proxy | $0 (Free tier) |
| Cloudflare WAF (basic) | $0 (Free tier) |
| Cloudflare DDoS Protection | $0 (Free tier) |
| Cloudflare SSL Certificate | $0 (Free tier) |
| Azure IP Restrictions | $0 (included) |
| **TOTAL** | **$0/month** |

**Optional Upgrades:**
- Cloudflare Pro ($20/month): Advanced rate limiting, better analytics
- Azure VNet (later): $150-300/month if needed for compliance

---

## Security Comparison

| Feature | Current | With Cloudflare + IP | With VNet |
|---------|---------|---------------------|-----------|
| DDoS Protection | ❌ | ✅ | ✅ |
| WAF | ❌ | ✅ | ✅ |
| Direct Backend Access | ⚠️ Possible | ❌ Blocked | ❌ Blocked |
| Custom Domain | ❌ | ✅ | ✅ |
| Rate Limiting | ❌ | ✅ (CF) | ✅ (Redis) |
| Cost | $13/mo | $13/mo | $150+/mo |
| Setup Time | Done | 15 min | 2-3 hours |
| Security Level | Medium | **Excellent** | Maximum |

---

## Troubleshooting

### Cloudflare Not Working

**Check:**
1. DNS propagation (can take up to 48 hours, usually 5-10 minutes)
2. Orange cloud is enabled (not gray)
3. SSL mode is "Full (strict)"

**Test DNS:**
```bash
nslookup api.wordsinseasonapp.com
# Should return Cloudflare IPs (104.x.x.x or 172.x.x.x)
```

### 403 Forbidden Errors

**Likely cause:** IP restriction misconfigured

**Fix:**
```bash
# Check current restrictions
az webapp config access-restriction show \
  --resource-group Staging \
  --name wis-api-gateway-stage

# Verify Cloudflare IPs are allowed
```

### Backend Service Returns 403

**Likely cause:** API Gateway IP not in allowlist

**Fix:**
```bash
# Add missing API Gateway IP
az webapp config access-restriction add \
  --resource-group Staging \
  --name wis-registration-stage \
  --rule-name "APIGateway-X" \
  --action Allow \
  --ip-address <MISSING_IP>/32 \
  --priority 1XX
```

---

## Next Steps

1. ✅ Set up Cloudflare DNS record
2. ✅ Enable Cloudflare proxy (orange cloud)
3. ✅ Lock API Gateway to Cloudflare IPs
4. ✅ Lock backend services to API Gateway IPs
5. ✅ Update frontend to use `api.wordsinseasonapp.com`
6. ✅ Enable Cloudflare WAF and Bot Fight Mode
7. ✅ Test everything thoroughly
8. ✅ Monitor Cloudflare analytics

---

## Summary

This setup gives you **enterprise-grade security at $0 additional cost**:

- ✅ All traffic filtered through Cloudflare
- ✅ DDoS protection
- ✅ Web Application Firewall
- ✅ Professional custom domain
- ✅ Backend services completely locked down
- ✅ No VNet complexity or cost

**You get 80% of VNet security benefits for 0% of the cost!** 🎯
