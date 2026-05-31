param(
    [string]$JavaExe = "C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot\bin\java.exe",
    [string]$MavenExe = "E:\java\group_buy_market\.tools\apache-maven-3.8.8\bin\mvn.cmd",
    [string]$AgentPath = ".tools\otel\opentelemetry-javaagent.jar",
    [string]$OtelConfig = "docs\observability\otel-javaagent.properties",
    [int]$MarketPort = 8091,
    [int]$MallPort = 8070,
    [switch]$SkipBuild,
    [switch]$SkipTracingInfra,
    [switch]$KeepExisting
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path ".").Path
$logDir = Join-Path $root "logs\otel"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

if (-not (Test-Path $JavaExe)) {
    throw "Java executable not found: $JavaExe"
}

if (-not $SkipTracingInfra) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root "scripts\observability\start-local-tracing.ps1")
}

& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root "scripts\observability\download-otel-javaagent.ps1") -AgentPath $AgentPath

$resolvedAgentPath = Join-Path $root $AgentPath
$resolvedOtelConfig = Join-Path $root $OtelConfig
if (-not (Test-Path $resolvedOtelConfig)) {
    throw "OpenTelemetry agent config not found: $resolvedOtelConfig"
}

if (-not $KeepExisting) {
    Get-CimInstance Win32_Process -Filter "name = 'java.exe'" |
            Where-Object { $_.CommandLine -like '*group-buy-market-app.jar*' -or $_.CommandLine -like '*s-pay-mall-ddd-app.jar*' } |
            ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
}

if (-not $SkipBuild) {
    $env:JAVA_HOME = Split-Path (Split-Path $JavaExe -Parent) -Parent
    $env:Path = "$env:JAVA_HOME\bin;$(Split-Path $MavenExe -Parent);$env:Path"
    Push-Location (Join-Path $root "qsyy-commerce-market")
    & $MavenExe -q -DskipTests package
    Pop-Location
    Push-Location (Join-Path $root "qsyy-commerce-mall")
    & $MavenExe -q -DskipTests package
    Pop-Location
}

function Start-OtelService([string]$ServiceName, [string]$JarPath, [int]$Port, [string[]]$ExtraArgs) {
    if (-not (Test-Path $JarPath)) {
        throw "Jar not found: $JarPath"
    }

    $commonArgs = @(
        "-javaagent:$resolvedAgentPath",
        "-Dotel.javaagent.configuration-file=$resolvedOtelConfig",
        "-Dotel.service.name=$ServiceName",
        "-Dotel.resource.attributes=service.namespace=my-ecommerce,deployment.environment=local,service.instance.id=$ServiceName-$Port",
        "-jar",
        $JarPath,
        "--server.port=$Port",
        "--spring.profiles.active=dev"
    ) + $ExtraArgs

    $out = Join-Path $logDir "$ServiceName-$Port.out.log"
    $err = Join-Path $logDir "$ServiceName-$Port.err.log"
    $proc = Start-Process -FilePath $JavaExe -ArgumentList $commonArgs -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
    Write-Host "Started $ServiceName on http://127.0.0.1:$Port pid=$($proc.Id)"
}

$marketJar = Join-Path $root "qsyy-commerce-market\group-buy-market-app\target\group-buy-market-app.jar"
$mallJar = Join-Path $root "qsyy-commerce-mall\s-pay-mall-ddd-app\target\s-pay-mall-ddd-app.jar"

Start-OtelService "group-buy-market" $marketJar $MarketPort @("--app.seckill.order-create-buffer.mode=redis_stream")
Start-OtelService "s-pay-mall-ddd" $mallJar $MallPort @("--app.config.group-buy-market.api-url=http://127.0.0.1:$MarketPort")

function Wait-HttpHealth([string]$Name, [int]$Port) {
    $deadline = (Get-Date).AddSeconds(90)
    do {
        try {
            Invoke-RestMethod -Uri "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 8 | Out-Null
            Write-Host "$Name health UP on http://127.0.0.1:$Port"
            return
        } catch {
            Start-Sleep -Seconds 3
        }
    } while ((Get-Date) -lt $deadline)
    throw "$Name did not become healthy on port $Port within 90 seconds."
}

Wait-HttpHealth "group-buy-market" $MarketPort
Wait-HttpHealth "s-pay-mall-ddd" $MallPort

Write-Host "Services are UP with OpenTelemetry Java agent."
Write-Host "Jaeger UI: http://127.0.0.1:16686"
Write-Host "Service logs: $logDir"
