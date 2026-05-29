param(
    [string]$ComposeFile = "docs\dev-ops\docker-compose-core-capacity.yml",
    [int]$SeckillRate = 300,
    [int]$GroupBuyRate = 100,
    [string]$Duration = "2m"
)

$ErrorActionPreference = "Stop"

Write-Host "Building latest jar..."
& ".\.tools\apache-maven-3.8.8\bin\mvn.cmd" -q -DskipTests package

Write-Host "Starting 3 marketing service instances..."
docker compose -f $ComposeFile up -d --build

Write-Host "Waiting for instances..."
Start-Sleep -Seconds 20

$hosts = @("http://127.0.0.1:8091", "http://127.0.0.1:8092", "http://127.0.0.1:8093")
foreach ($hostUrl in $hosts) {
    try {
        Invoke-RestMethod -Uri "$hostUrl/actuator/health" -TimeoutSec 5 | Out-Null
        Write-Host "$hostUrl UP"
    } catch {
        Write-Warning "$hostUrl health check failed: $($_.Exception.Message)"
    }
}

Write-Host "Run seckill capacity test on instance 1..."
$env:HOST = "http://127.0.0.1:8091"
$env:RATE = "$SeckillRate"
$env:DURATION = $Duration
k6 run scripts\k6\seckill-lock.js

Write-Host "Run group-buy same-team test on instance 2..."
$env:MARKET_BASE_URL = "http://127.0.0.1:8092"
$env:MODE = "same_team"
$env:RATE = "$GroupBuyRate"
$env:DURATION = $Duration
k6 run scripts\k6\group-buy-lock.js

Write-Host "Capacity run completed. Export Prometheus/Grafana screenshots separately if needed."
