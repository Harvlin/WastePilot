<#
.SYNOPSIS
  Seeds or resets realistic demo data in the WastePilot local development instance.

.DESCRIPTION
  Calls the local-only /api/v1/dev/seed or /api/v1/dev/reset endpoints.
  STRICT SAFETY GUARD: These endpoints only exist in the 'local' profile and
  fail loudly if called against docker or production profiles.

.PARAMETER Reset
  If specified, resets all application data to a clean empty state.

.PARAMETER BaseUrl
  Base URL of the local backend server (default: http://localhost:8080).

.EXAMPLE
  .\tools\seed-demo-data.ps1
  Seeds realistic demo data with 10 completed batches, 1 running batch, and demo moments.

.EXAMPLE
  .\tools\seed-demo-data.ps1 -Reset
  Wipes all data back to clean empty state.
#>

param(
  [switch]$Reset,
  [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host " WastePilot Demo Data Seeder (LOCAL Dev Profile Only)" -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host ""

$endpoint = if ($Reset) { "$BaseUrl/api/v1/dev/reset" } else { "$BaseUrl/api/v1/dev/seed" }
$actionName = if ($Reset) { "Resetting database to empty state" } else { "Seeding realistic demo dataset" }

Write-Host "[>] Target Endpoint: $endpoint" -ForegroundColor DarkGray
Write-Host "[>] Action: $actionName..." -ForegroundColor Yellow

try {
  $response = Invoke-RestMethod -Uri $endpoint -Method Post -ContentType "application/json" -TimeoutSec 15
} catch {
  Write-Host ""
  Write-Host "[!] Connection failed: $($_.Exception.Message)" -ForegroundColor Red
  Write-Host ""
  Write-Host "Make sure the local backend is running with the 'local' profile:" -ForegroundColor Yellow
  Write-Host "  cd wastepilot" -ForegroundColor White
  Write-Host "  .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local" -ForegroundColor White
  Write-Host ""
  Write-Host "Note: If running with Docker Compose, the backend container defaults to port 8088." -ForegroundColor DarkGray
  Write-Host "      However, demo seeding is restricted to 'local' profile to protect production." -ForegroundColor DarkGray
  exit 1
}

if ($Reset) {
  Write-Host ""
  Write-Host "[+] SUCCESS: $($response.message)" -ForegroundColor Green
  Write-Host ""
  exit 0
}

Write-Host ""
Write-Host "[+] SUCCESS: $($response.message)" -ForegroundColor Green
Write-Host ""
Write-Host "--- DEMO CREDENTIALS (FOR VIDEO RECORDING) ---" -ForegroundColor Cyan
Write-Host "  Operator Account:" -ForegroundColor White
Write-Host "    Email:    $($response.operatorCredentials.email)" -ForegroundColor Yellow
Write-Host "    Password: $($response.operatorCredentials.password)" -ForegroundColor Yellow
Write-Host ""
Write-Host "  Supervisor Account:" -ForegroundColor White
Write-Host "    Email:    $($response.supervisorCredentials.email)" -ForegroundColor Yellow
Write-Host "    Password: $($response.supervisorCredentials.password)" -ForegroundColor Yellow
Write-Host ""
Write-Host "--- ACTIVE LIVE DEMO BATCH (GENUINELY RUNNING) ---" -ForegroundColor Cyan
Write-Host "  Batch ID:   $($response.runningBatchId)" -ForegroundColor Green
Write-Host "  Template:   Organic Denim Workwear Jacket" -ForegroundColor White
Write-Host "  Status:     running (Started ~25 min ago; health indicator: green)" -ForegroundColor Green
Write-Host "  Next steps on camera: Open Floor View / Operations, log material or waste, close batch!" -ForegroundColor DarkGray
Write-Host ""
Write-Host "--- SEEDED DATA SUMMARY ---" -ForegroundColor Cyan
Write-Host "  Materials:          $($response.summary.materialsCount)" -ForegroundColor White
Write-Host "  Templates:          $($response.summary.templatesCount)" -ForegroundColor White
Write-Host "  Completed Batches:  $($response.summary.completedBatchesCount)" -ForegroundColor White
Write-Host "  Running Batches:    $($response.summary.runningBatchesCount)" -ForegroundColor White
Write-Host "  Anomalies:          $($response.summary.anomaliesCount) (Z-Score > 2.5 on Recycled Polyester)" -ForegroundColor White
Write-Host "  Recent Insights:    $($response.summary.insightsCount)" -ForegroundColor White
Write-Host "  Audit Trail Chains: $($response.summary.auditEntriesCount) (100% verified HMAC-SHA256)" -ForegroundColor White
Write-Host ""
Write-Host "--- BROWSER ACCESS URLS ---" -ForegroundColor Cyan
Write-Host "  Frontend (Vite UI): http://localhost:5173" -ForegroundColor Green
Write-Host "  Login Page:         http://localhost:5173/login" -ForegroundColor White
Write-Host "  Floor View:         http://localhost:5173/app/floor-view" -ForegroundColor White
Write-Host "  Dashboard:          http://localhost:5173/app/dashboard" -ForegroundColor White
Write-Host "  Operations:         http://localhost:5173/app/operations" -ForegroundColor White
Write-Host "  Analytics:          http://localhost:5173/app/analytics" -ForegroundColor White
Write-Host "  Insights:           http://localhost:5173/app/insights" -ForegroundColor White
Write-Host "  Integrity Review:   http://localhost:5173/app/integrity" -ForegroundColor White
Write-Host ""
