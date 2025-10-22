# GitHub Actions CI/CD Setup

The API Gateway deployment is currently failing because GitHub Actions secrets are not configured. Follow these steps to fix it:

## 🔑 Required GitHub Secrets

You need to add two secrets to your GitHub repository:

1. `AZURE_WEBAPP_PUBLISH_PROFILE_PROD` - Production publish profile
2. `AZURE_WEBAPP_PUBLISH_PROFILE_STAGE` - Staging publish profile

## 📋 Step-by-Step Instructions

### 1. Get the Publish Profiles

Run these commands to get the publish profiles (already done for you below):

```bash
# Production
az webapp deployment list-publishing-profiles \
  --resource-group Production \
  --name wis-api-gateway \
  --xml

# Staging
az webapp deployment list-publishing-profiles \
  --resource-group Staging \
  --name wis-api-gateway-stage \
  --xml
```

### 2. Add Secrets to GitHub

1. Go to your GitHub repository: https://github.com/TrentCorrill/wis-api-gateway
2. Click **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**

**For Production:**
- Name: `AZURE_WEBAPP_PUBLISH_PROFILE_PROD`
- Value: Copy the ENTIRE XML output from the production command above
- Click **Add secret**

**For Staging:**
- Name: `AZURE_WEBAPP_PUBLISH_PROFILE_STAGE`
- Value: Copy the ENTIRE XML output from the staging command above
- Click **Add secret**

### 3. Get the Publish Profiles

I'll retrieve them for you now:

**Production Publish Profile:**
```bash
az webapp deployment list-publishing-profiles \
  --resource-group Production \
  --name wis-api-gateway \
  --xml > /tmp/prod-publish-profile.xml

cat /tmp/prod-publish-profile.xml
```

**Staging Publish Profile:**
```bash
az webapp deployment list-publishing-profiles \
  --resource-group Staging \
  --name wis-api-gateway-stage \
  --xml > /tmp/stage-publish-profile.xml

cat /tmp/stage-publish-profile.xml
```

---

## 🚀 How to Add the Secrets

### Using the GitHub UI (Recommended)

1. **Navigate to Repository Settings:**
   - Go to https://github.com/TrentCorrill/wis-api-gateway/settings/secrets/actions

2. **Add Production Secret:**
   - Click "New repository secret"
   - Name: `AZURE_WEBAPP_PUBLISH_PROFILE_PROD`
   - Value: Paste the entire XML from the production command
   - Click "Add secret"

3. **Add Staging Secret:**
   - Click "New repository secret"
   - Name: `AZURE_WEBAPP_PUBLISH_PROFILE_STAGE`
   - Value: Paste the entire XML from the staging command
   - Click "Add secret"

### Using GitHub CLI (Alternative)

```bash
# Save publish profiles to files
az webapp deployment list-publishing-profiles \
  --resource-group Production \
  --name wis-api-gateway \
  --xml > prod-profile.xml

az webapp deployment list-publishing-profiles \
  --resource-group Staging \
  --name wis-api-gateway-stage \
  --xml > stage-profile.xml

# Add secrets using gh CLI
gh secret set AZURE_WEBAPP_PUBLISH_PROFILE_PROD < prod-profile.xml
gh secret set AZURE_WEBAPP_PUBLISH_PROFILE_STAGE < stage-profile.xml

# Clean up
rm prod-profile.xml stage-profile.xml
```

---

## ✅ Verify Setup

After adding the secrets:

1. **Trigger a new deployment:**
   ```bash
   git commit --allow-empty -m "Trigger CI/CD"
   git push origin main
   ```

2. **Watch the workflow:**
   - Go to https://github.com/TrentCorrill/wis-api-gateway/actions
   - You should see the workflow running
   - Deployment should succeed this time

3. **Verify deployment:**
   ```bash
   curl https://api.wordsinseasonapp.com/actuator/health
   # Should return: {"status":"UP"}
   ```

---

## 🔄 How Deployments Work

**Automatic Deployments:**
- **Push to `main`** → Deploys to **Production**
- **Push to `develop`** → Deploys to **Staging**

**Manual Deployment:**
- Go to Actions tab → Select workflow → Click "Run workflow"

**Deployment Process:**
1. Build and test the application
2. Upload JAR artifact
3. Download JAR in deployment job
4. Deploy to Azure using publish profile
5. Wait 30 seconds for startup
6. Run health check
7. Run smoke tests (production only)

---

## 🐛 Troubleshooting

### Deployment Still Failing?

**Check the logs:**
```bash
gh run list --limit 1
gh run view --log
```

**Common issues:**

1. **"No credentials found"** - Secrets not added correctly
   - Solution: Double-check secret names match exactly

2. **"Deployment failed"** - Azure issue
   - Solution: Check Azure portal for app service status

3. **"Health check failed"** - Application not starting
   - Solution: Check Azure logs: `az webapp log tail --resource-group Production --name wis-api-gateway`

### Still Having Issues?

Check the deployment logs in Azure:
```bash
# Production logs
az webapp log tail --resource-group Production --name wis-api-gateway

# Staging logs
az webapp log tail --resource-group Staging --name wis-api-gateway-stage
```

---

## 📝 Next Steps

After fixing the GitHub Actions:

1. ✅ Add the two secrets to GitHub
2. ✅ Push a commit to trigger deployment
3. ✅ Verify deployment succeeded
4. ✅ Test the API Gateway

Then you're done! All future pushes to `main` will auto-deploy to production, and pushes to `develop` will auto-deploy to staging.

---

## 📚 Reference

- **Workflow file:** `.github/workflows/deploy.yml`
- **GitHub Actions:** https://github.com/TrentCorrill/wis-api-gateway/actions
- **Azure Portal:** https://portal.azure.com

**Deployment Status:**
- Production: https://wis-api-gateway.azurewebsites.net/actuator/health
- Staging: https://wis-api-gateway-stage.azurewebsites.net/actuator/health
- Public API: https://api.wordsinseasonapp.com/actuator/health
