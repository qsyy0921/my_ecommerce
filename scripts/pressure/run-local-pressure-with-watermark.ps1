param(
    [string]$PressureCommand = "powershell -NoProfile -ExecutionPolicy Bypass -File scripts\pressure\run-local-pressure-matrix.ps1 -MarketHost http://127.0.0.1:8091 -SeckillConcurrencySteps 100 -SeckillTotalPerStep 200 -GroupBuyConcurrencySteps 50 -GroupBuyTotalPerStep 100",
    [string]$OutputDir = "",
    [int]$MaxDurationSeconds = 300,
    [int]$IntervalSeconds = 5
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path (Resolve-Path ".").Path ("docs\capacity-results\" + (Get-Date -Format "yyyyMMdd-HHmmss") + "-pressure-watermark")
}
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$stopFile = Join-Path $OutputDir ".stop-watermark"
$collectorOut = Join-Path $OutputDir "collector.out.log"
$collectorErr = Join-Path $OutputDir "collector.err.log"

$collectorArgs = @(
    "-NoProfile",
    "-ExecutionPolicy",
    "Bypass",
    "-File",
    "scripts\pressure\collect-resource-watermark.ps1",
    "-OutputDir",
    $OutputDir,
    "-DurationSeconds",
    "$MaxDurationSeconds",
    "-IntervalSeconds",
    "$IntervalSeconds",
    "-StopFile",
    $stopFile
)

$collector = Start-Process -FilePath "powershell.exe" -ArgumentList $collectorArgs -WindowStyle Hidden `
    -RedirectStandardOutput $collectorOut -RedirectStandardError $collectorErr -PassThru

try {
    Write-Host "Resource collector started pid=$($collector.Id)"
    Write-Host "Pressure command: $PressureCommand"
    Invoke-Expression $PressureCommand
    $pressureSucceeded = $?
    $pressureExitCode = $LASTEXITCODE
    if (-not $pressureSucceeded) {
        throw "Pressure command failed"
    }
    if ($null -ne $pressureExitCode -and $pressureExitCode -ne 0) {
        throw "Pressure command failed with exit code $pressureExitCode"
    }
} finally {
    New-Item -ItemType File -Force -Path $stopFile | Out-Null
    Wait-Process -Id $collector.Id -Timeout 60 -ErrorAction SilentlyContinue
    if (-not $collector.HasExited) {
        Stop-Process -Id $collector.Id -Force
    }
}

Write-Host "Pressure watermark output: $OutputDir"
