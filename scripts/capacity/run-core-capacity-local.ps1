param(
    [string]$JavaExe = "java",
    [int[]]$Ports = @(8091, 8092, 8093),
    [int]$SeckillTotal = 1000,
    [int]$SeckillConcurrency = 200,
    [int]$GroupBuyTotal = 100,
    [int]$GroupBuyConcurrency = 50,
    [long]$SeckillActivityId = 990529,
    [string]$SeckillGoodsId = "9890529",
    [long]$GroupBuyActivityId = 100123,
    [string]$GroupBuyGoodsId = "9890001",
    [switch]$SkipBuild,
    [switch]$SkipSeed
)

$ErrorActionPreference = "Stop"

function Test-PortOpen([int]$Port) {
    return (Test-NetConnection 127.0.0.1 -Port $Port -WarningAction SilentlyContinue).TcpTestSucceeded
}

function Invoke-Pressure([string]$Scenario, [string]$HostUrl, [int]$Total, [int]$Concurrency, [long]$ActivityId, [string]$GoodsId, [string]$OutputPath) {
    $env:MARKET_HOST = $HostUrl
    $env:SCENARIO = $Scenario
    $env:TOTAL = "$Total"
    $env:CONCURRENCY = "$Concurrency"
    $env:ACTIVITY_ID = "$ActivityId"
    $env:GOODS_ID = $GoodsId
    $env:WARMUP = if ($Scenario -eq "seckill-lock") { "true" } else { "false" }
    $env:QUERY_RESULT = "false"
    node scripts\pressure\seckill-pressure.js | Tee-Object -FilePath $OutputPath
}

$root = Resolve-Path "."
$jar = Join-Path $root "group-buy-market-master\group-buy-market-app\target\group-buy-market-app.jar"
$logDir = Join-Path $root "group-buy-market-master\group-buy-market-app\target\capacity-local"
$resultDir = Join-Path $root ("docs\capacity-results\" + (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Force -Path $logDir, $resultDir | Out-Null

foreach ($port in @(23306, 26379, 5672)) {
    if (-not (Test-PortOpen $port)) {
        throw "Required local infra port $port is not open. Start gbm-mysql, gbm-redis and gbm-rabbitmq first."
    }
}

if (-not $SkipBuild) {
    & ".\.tools\apache-maven-3.8.8\bin\mvn.cmd" -q -DskipTests package
}

if (-not $SkipSeed) {
@"
insert into sku(source, channel, goods_id, goods_name, original_price)
values('s01', 'c01', '$SeckillGoodsId', 'capacity test sku', 100.00)
on duplicate key update goods_name=values(goods_name), original_price=values(original_price);
insert into seckill_activity(activity_id, activity_name, source, channel, goods_id, seckill_price, total_count, available_count, lock_count, take_limit_count, status, start_time, end_time)
values($SeckillActivityId, 'capacity seckill activity', 's01', 'c01', '$SeckillGoodsId', 69.00, 10000, 10000, 0, 1, 1, '2026-01-01 00:00:00', '2029-12-31 23:59:59')
on duplicate key update available_count=10000, total_count=10000, lock_count=0, status=1, start_time='2026-01-01 00:00:00', end_time='2029-12-31 23:59:59';
"@ | docker exec -i gbm-mysql mysql -uroot -p123456 group_buy_market
}

foreach ($port in $Ports) {
    if (Test-PortOpen $port) {
        Write-Host "http://127.0.0.1:$port already UP"
        continue
    }
    $out = Join-Path $logDir "run-$port.out.log"
    $err = Join-Path $logDir "run-$port.err.log"
    $cmd = "`$env:SERVER_PORT='$port'; `$env:SPRING_PROFILES_ACTIVE='dev'; `$env:APP_SECKILL_ORDER_CREATE_BUFFER_MODE='redis_stream'; & '$JavaExe' -jar '$jar'"
    $proc = Start-Process -FilePath "powershell.exe" -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $cmd) -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
    Write-Host "Started http://127.0.0.1:$port pid=$($proc.Id)"
}

Start-Sleep -Seconds 25
foreach ($port in $Ports) {
    Invoke-RestMethod -Uri "http://127.0.0.1:$port/actuator/health" -TimeoutSec 8 | Out-Null
    Write-Host "http://127.0.0.1:$port health UP"
}

Invoke-Pressure "seckill-query" "http://127.0.0.1:$($Ports[0])" 1000 100 $SeckillActivityId $SeckillGoodsId (Join-Path $resultDir "seckill-query.json")
Invoke-Pressure "seckill-lock" "http://127.0.0.1:$($Ports[0])" $SeckillTotal $SeckillConcurrency $SeckillActivityId $SeckillGoodsId (Join-Path $resultDir "seckill-lock.json")
Invoke-Pressure "group-buy-lock" "http://127.0.0.1:$($Ports[1])" $GroupBuyTotal $GroupBuyConcurrency $GroupBuyActivityId $GroupBuyGoodsId (Join-Path $resultDir "group-buy-lock.json")

Write-Host "Capacity results: $resultDir"
