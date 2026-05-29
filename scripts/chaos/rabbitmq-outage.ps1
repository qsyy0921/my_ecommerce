param(
  [string]$Container = "gbm-rabbitmq",
  [int]$PauseSeconds = 10
)

$ErrorActionPreference = "Stop"
Write-Host "pause $Container for $PauseSeconds seconds"
docker pause $Container | Out-Host
Start-Sleep -Seconds $PauseSeconds
docker unpause $Container | Out-Host
Write-Host "rabbitmq outage drill finished"
