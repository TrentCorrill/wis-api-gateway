# Frontend Integration Guide

This guide shows how to update the Words in Season frontend to use the API Gateway.

## Overview

**Before (Direct Service Calls):**
```
Frontend → wis-registration.azurewebsites.net
        → wis-subscriptions.azurewebsites.net
        → wis-message-handler.azurewebsites.net
```

**After (Through API Gateway):**
```
Frontend → API Gateway → [Registration, Subscriptions, Messages]
```

## Step 1: Update Environment Configuration

### For Static Web App

Add application settings in Azure Portal or via CLI:

```bash
# Staging
az staticwebapp appsettings set \
  --name words-in-season-stage \
  --setting-names \
    API_BASE_URL="https://wis-api-gateway-stage.azurewebsites.net" \
    API_KEY="<your-staging-api-key>"

# Production
az staticwebapp appsettings set \
  --name words-in-season \
  --setting-names \
    API_BASE_URL="https://wis-api-gateway.azurewebsites.net" \
    API_KEY="<your-production-api-key>"
```

### For Local Development

Create `.env.local` file:

```bash
API_BASE_URL=http://localhost:8080
API_KEY=local-dev-key-12345
```

## Step 2: Update API Client

### Option A: Centralized API Client (Recommended)

Create or update `src/services/api-client.js`:

```javascript
// src/services/api-client.js

const API_BASE_URL = process.env.API_BASE_URL || 'https://wis-api-gateway.azurewebsites.net';
const API_KEY = process.env.API_KEY;

class ApiClient {
  constructor() {
    this.baseUrl = API_BASE_URL;
    this.apiKey = API_KEY;
  }

  /**
   * Makes an API request with automatic API key authentication
   */
  async request(endpoint, options = {}) {
    const url = `${this.baseUrl}${endpoint}`;

    const headers = {
      'Content-Type': 'application/json',
      'X-API-Key': this.apiKey,
      ...options.headers,
    };

    const config = {
      ...options,
      headers,
    };

    try {
      const response = await fetch(url, config);

      if (!response.ok) {
        throw new ApiError(response.status, await response.text());
      }

      // Handle no content responses
      if (response.status === 204) {
        return null;
      }

      return await response.json();
    } catch (error) {
      console.error(`API request failed: ${endpoint}`, error);
      throw error;
    }
  }

  // Convenience methods
  get(endpoint, options = {}) {
    return this.request(endpoint, { ...options, method: 'GET' });
  }

  post(endpoint, data, options = {}) {
    return this.request(endpoint, {
      ...options,
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  put(endpoint, data, options = {}) {
    return this.request(endpoint, {
      ...options,
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  delete(endpoint, options = {}) {
    return this.request(endpoint, { ...options, method: 'DELETE' });
  }
}

class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
    this.name = 'ApiError';
  }
}

// Export singleton instance
export default new ApiClient();
```

### Option B: Update Existing Service Files

If you have separate service files, update each one:

**Before:**
```javascript
// src/services/registration.service.js
const BASE_URL = 'https://wis-registration.azurewebsites.net';

export async function registerUser(userData) {
  const response = await fetch(`${BASE_URL}/api/register`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(userData),
  });

  return response.json();
}
```

**After:**
```javascript
// src/services/registration.service.js
import apiClient from './api-client';

export async function registerUser(userData) {
  return apiClient.post('/api/register', userData);
}
```

## Step 3: Update All Service Endpoints

### Registration Service

```javascript
// src/services/registration.service.js
import apiClient from './api-client';

export const registrationService = {
  register: (userData) => apiClient.post('/api/register', userData),

  getProfile: (userId) => apiClient.get(`/api/register/${userId}`),

  updateProfile: (userId, data) => apiClient.put(`/api/register/${userId}`, data),
};
```

### Subscriptions Service

```javascript
// src/services/subscription.service.js
import apiClient from './api-client';

export const subscriptionService = {
  // Get user's subscriptions
  getSubscriptions: (customerId) =>
    apiClient.get(`/api/subscriptions/customer/${customerId}`),

  // Create new subscription
  createSubscription: (data) =>
    apiClient.post('/api/subscriptions', data),

  // Cancel subscription
  cancelSubscription: (subscriptionId, reason) =>
    apiClient.post(`/api/subscriptions/${subscriptionId}/cancel`, { reason }),

  // Get subscription by ID
  getSubscription: (subscriptionId) =>
    apiClient.get(`/api/subscriptions/${subscriptionId}`),
};
```

### Messages Service

```javascript
// src/services/message.service.js
import apiClient from './api-client';

export const messageService = {
  // Get messages for customer
  getMessages: (customerId, params = {}) => {
    const query = new URLSearchParams(params).toString();
    return apiClient.get(`/api/messages?customerId=${customerId}&${query}`);
  },

  // Send message
  sendMessage: (data) =>
    apiClient.post('/api/messages/send', data),

  // Get message history
  getHistory: (customerId) =>
    apiClient.get(`/api/messages/history/${customerId}`),
};
```

## Step 4: Error Handling

Add proper error handling for API Gateway specific errors:

```javascript
// src/services/api-client.js

class ApiClient {
  // ... existing code ...

  async request(endpoint, options = {}) {
    const url = `${this.baseUrl}${endpoint}`;

    const headers = {
      'Content-Type': 'application/json',
      'X-API-Key': this.apiKey,
      ...options.headers,
    };

    try {
      const response = await fetch(url, { ...options, headers });

      // Handle specific HTTP status codes
      switch (response.status) {
        case 401:
          // Invalid or missing API key
          throw new ApiError(401, 'Authentication failed. Please contact support.');

        case 429:
          // Rate limit exceeded
          const retryAfter = response.headers.get('X-RateLimit-Reset');
          throw new ApiError(429, `Rate limit exceeded. Try again in ${retryAfter} seconds.`);

        case 500:
        case 502:
        case 503:
          // Server errors
          throw new ApiError(response.status, 'Service temporarily unavailable. Please try again later.');

        case 200:
        case 201:
        case 204:
          // Success
          break;

        default:
          if (!response.ok) {
            const errorData = await response.json();
            throw new ApiError(response.status, errorData.message || 'Request failed');
          }
      }

      if (response.status === 204) {
        return null;
      }

      return await response.json();

    } catch (error) {
      if (error instanceof ApiError) {
        throw error;
      }

      // Network errors
      console.error(`Network error: ${endpoint}`, error);
      throw new ApiError(0, 'Network error. Please check your connection.');
    }
  }
}
```

## Step 5: Update React Components (Example)

### Registration Component

**Before:**
```javascript
// src/components/Registration.jsx
const handleSubmit = async (formData) => {
  try {
    const response = await fetch('https://wis-registration.azurewebsites.net/api/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(formData),
    });

    const data = await response.json();
    // Handle success
  } catch (error) {
    // Handle error
  }
};
```

**After:**
```javascript
// src/components/Registration.jsx
import { registrationService } from '../services/registration.service';

const handleSubmit = async (formData) => {
  try {
    const data = await registrationService.register(formData);
    // Handle success
  } catch (error) {
    if (error.status === 429) {
      setError('Too many requests. Please wait a moment.');
    } else {
      setError(error.message);
    }
  }
};
```

## Step 6: Testing

### Local Testing

1. Start API Gateway locally:
```bash
cd /path/to/wis-api-gateway
./gradlew bootRun
```

2. Update frontend `.env.local`:
```bash
API_BASE_URL=http://localhost:8080
API_KEY=local-dev-key-12345
```

3. Test registration flow end-to-end

### Staging Testing

1. Deploy API Gateway to staging
2. Update frontend environment variables
3. Deploy frontend to staging
4. Test all flows:
   - Registration
   - Login
   - Subscription creation
   - Message sending

## Step 7: Monitoring Integration (Optional)

Track API Gateway requests in your frontend:

```javascript
// src/services/api-client.js

class ApiClient {
  async request(endpoint, options = {}) {
    const startTime = performance.now();

    try {
      const response = await fetch(url, config);

      // Log successful requests
      const duration = performance.now() - startTime;
      this.logRequest(endpoint, 'success', duration);

      return await response.json();

    } catch (error) {
      // Log failed requests
      const duration = performance.now() - startTime;
      this.logRequest(endpoint, 'error', duration, error);

      throw error;
    }
  }

  logRequest(endpoint, status, duration, error = null) {
    // Send to analytics
    if (window.gtag) {
      window.gtag('event', 'api_request', {
        endpoint,
        status,
        duration: Math.round(duration),
        error: error?.message,
      });
    }

    // Or send to Application Insights
    if (window.appInsights) {
      window.appInsights.trackDependency({
        name: endpoint,
        duration,
        success: status === 'success',
        resultCode: error?.status || 200,
      });
    }
  }
}
```

## Step 8: Security Best Practices

### Don't Hardcode API Keys

❌ **Bad:**
```javascript
const API_KEY = 'abc123...'; // Hardcoded
```

✅ **Good:**
```javascript
const API_KEY = process.env.API_KEY; // From environment
```

### Store API Key Securely

For Azure Static Web Apps, use Application Settings (not in code):

```bash
az staticwebapp appsettings set \
  --name words-in-season \
  --setting-names API_KEY="your-key-here"
```

### Validate API Key on Load

```javascript
// src/App.jsx
useEffect(() => {
  if (!process.env.API_KEY) {
    console.error('API_KEY not configured!');
    // Show error to user or redirect
  }
}, []);
```

## Rollback Plan

If issues occur after switching to API Gateway:

1. **Quick rollback** - Update environment variables to point back to direct services:
```bash
az staticwebapp appsettings set \
  --name words-in-season \
  --setting-names \
    API_BASE_URL="https://wis-registration.azurewebsites.net" \
    USE_GATEWAY="false"
```

2. **Code rollback** - Revert API client changes via Git:
```bash
git revert <commit-hash>
git push
```

## Troubleshooting

### 401 Unauthorized
- Check API key is set correctly in environment
- Verify API key is valid in Key Vault
- Check X-API-Key header is being sent

### 429 Rate Limited
- Reduce request frequency
- Implement request batching
- Check rate limit headers in response

### CORS Errors
- Verify frontend domain is in CORS_ALLOWED_ORIGINS
- Check browser console for exact error
- Ensure preflight requests (OPTIONS) work

### Network Errors
- Check API Gateway is running (health endpoint)
- Verify firewall/network settings
- Test with curl to isolate frontend vs. network issue

## Next Steps

1. Update all frontend service files to use API client
2. Test locally with all features
3. Deploy to staging and test
4. Monitor for errors in Application Insights
5. Deploy to production during low-traffic period
6. Monitor closely for 24 hours

## Support

If you encounter issues:
- Check API Gateway logs: `az webapp log tail`
- Check browser console for errors
- Verify API key is correct
- Test with curl to isolate the issue
