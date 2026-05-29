param(
  [string]$Container = "gbm-mysql",
  [int]$PauseSeconds = 5
)

$ErrorActionPreference = "Stop"
Write-Host "pause $Container for $PauseSeconds seconds"
docker pause $Container | Out-Host
Start-Sleep -Seconds $PauseSeconds
docker unpause $Container | Out-Host
Write-Host "mysql pause drill finished"
