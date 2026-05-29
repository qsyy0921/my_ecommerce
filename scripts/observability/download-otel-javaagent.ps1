param(
    [string]$AgentPath = ".tools\otel\opentelemetry-javaagent.jar",
    [string]$DownloadUrl = "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar",
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$resolvedAgentPath = Join-Path (Resolve-Path ".").Path $AgentPath
$agentDir = Split-Path $resolvedAgentPath -Parent
New-Item -ItemType Directory -Force -Path $agentDir | Out-Null

if ((Test-Path $resolvedAgentPath) -and -not $Force) {
    $existing = Get-Item $resolvedAgentPath
    if ($existing.Length -gt 0) {
        Write-Host "OpenTelemetry Java agent already exists: $resolvedAgentPath"
        return
    }
}

Write-Host "Downloading OpenTelemetry Java agent..."
Write-Host $DownloadUrl
Invoke-WebRequest -Uri $DownloadUrl -OutFile $resolvedAgentPath

$downloaded = Get-Item $resolvedAgentPath
if ($downloaded.Length -le 0) {
    throw "Downloaded agent is empty: $resolvedAgentPath"
}

Write-Host "OpenTelemetry Java agent ready: $resolvedAgentPath ($($downloaded.Length) bytes)"
