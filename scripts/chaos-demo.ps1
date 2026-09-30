# Turn the mock gateway hostile, then confirm the ledger still balances.
# The stack must already be running (docker compose up).
# Admin login uses the seeded local account.

$gateway = "http://localhost:8081"
$api = "http://localhost:8080"

$chaos = @{
  latencyMs = @{ min = 50; max = 400 }
  timeoutRate = 0.10
  errorRate = 0.05
  duplicateWebhookRate = 0.20
  delayedWebhookMs = @{ min = 0; max = 2000 }
  outOfOrderWebhooks = $true
  dropWebhookRate = 0.05
  settlementDriftRate = 0.01
} | ConvertTo-Json -Depth 4

Invoke-RestMethod -Method Post -Uri "$gateway/admin/chaos" -ContentType "application/json" -Body $chaos
Write-Host "Chaos is on. Top up a wallet from the app, wait a minute for recovery, then check integrity."

$login = Invoke-RestMethod -Method Post -Uri "$api/api/v1/auth/login" -ContentType "application/json" -Body '{"email":"admin@payflow.local","password":"admin-dev-change-me"}'
$headers = @{ Authorization = "Bearer $($login.accessToken)" }
$integrity = Invoke-RestMethod -Uri "$api/api/v1/admin/integrity" -Headers $headers
$integrity | ConvertTo-Json

$calm = @{
  latencyMs = @{ min = 0; max = 0 }
  timeoutRate = 0
  errorRate = 0
  duplicateWebhookRate = 0
  delayedWebhookMs = @{ min = 0; max = 0 }
  outOfOrderWebhooks = $false
  dropWebhookRate = 0
  settlementDriftRate = 0
} | ConvertTo-Json -Depth 4
Invoke-RestMethod -Method Post -Uri "$gateway/admin/chaos" -ContentType "application/json" -Body $calm
Write-Host "Chaos is off."
