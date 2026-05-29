param(
    [string]$MarketHost = "http://127.0.0.1:8080",
    [long]$SeckillActivityId = 990700,
    [string]$SeckillGoodsId = "9890700",
    [long]$GroupBuyActivityId = 100123,
    [string]$GroupBuyGoodsId = "9890001",
    [int]$SeckillStock = 20000,
    [int]$SeckillOrderShardCount = 1,
    [int]$StockBucketCount = 64,
    [string]$SeckillConcurrencySteps = "100,500,1000",
    [int]$SeckillTotalPerStep = 1000,
    [string]$GroupBuyConcurrencySteps = "50,100",
    [int]$GroupBuyTotalPerStep = 200,
    [int]$WaitSeconds = 20,
    [string]$MysqlContainer = "gbm-mysql",
    [string]$RedisContainer = "gbm-redis",
    [switch]$SkipSeed,
    [switch]$SkipGroupBuy
)

$ErrorActionPreference = "Stop"

function Test-HttpHealth([string]$BaseUrl) {
    try {
        $response = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 8
        return $response.status -eq "UP"
    } catch {
        return $false
    }
}

function Convert-StepList([string]$Value, [string]$Name) {
    $steps = @()
    foreach ($item in ($Value -split ",")) {
        $trimmed = $item.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed)) {
            continue
        }
        $parsed = 0
        if (-not [int]::TryParse($trimmed, [ref]$parsed) -or $parsed -le 0) {
            throw "Invalid $Name value: $trimmed"
        }
        $steps += $parsed
    }
    if ($steps.Count -eq 0) {
        throw "$Name must contain at least one positive integer."
    }
    return $steps
}

function Invoke-MysqlScript([string]$Sql) {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $Sql | docker exec -i $MysqlContainer mysql -uroot -p123456 group_buy_market 2>$null
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    if ($exitCode -ne 0) {
        throw "MySQL script failed."
    }
}

function Invoke-Pressure([string]$Scenario, [int]$Total, [int]$Concurrency, [long]$ActivityId, [string]$GoodsId, [string]$UserPrefix, [string]$OutputPath, [bool]$Warmup) {
    $env:MARKET_HOST = $MarketHost
    $env:SCENARIO = $Scenario
    $env:TOTAL = "$Total"
    $env:CONCURRENCY = "$Concurrency"
    $env:ACTIVITY_ID = "$ActivityId"
    $env:GOODS_ID = $GoodsId
    $env:USER_PREFIX = $UserPrefix
    $env:WARMUP = if ($Warmup) { "true" } else { "false" }
    $env:QUERY_RESULT = "false"
    node scripts\pressure\seckill-pressure.js | Tee-Object -FilePath $OutputPath
    if ($LASTEXITCODE -ne 0) {
        throw "Pressure scenario $Scenario failed."
    }
}

function Reset-SeckillData {
    $deleteParts = @()
    if ($SeckillOrderShardCount -le 1) {
        $deleteParts += "delete from seckill_order where activity_id = $SeckillActivityId;"
    } else {
        for ($i = 0; $i -lt $SeckillOrderShardCount; $i++) {
            $tableName = "seckill_order_{0:D2}" -f $i
            $deleteParts += "delete from ``$tableName`` where activity_id = $SeckillActivityId;"
        }
    }
    $deleteSql = $deleteParts -join "`n"
    Invoke-MysqlScript @"
insert into sku(source, channel, goods_id, goods_name, original_price)
values('s01', 'c01', '$SeckillGoodsId', 'local pressure sku', 100.00)
on duplicate key update goods_name = values(goods_name), original_price = values(original_price);

$deleteSql
delete from seckill_stock_flow where activity_id = $SeckillActivityId;

insert into seckill_activity(activity_id, activity_name, source, channel, goods_id, seckill_price, total_count, available_count, lock_count, take_limit_count, status, start_time, end_time)
values($SeckillActivityId, 'local pressure seckill', 's01', 'c01', '$SeckillGoodsId', 69.00, $SeckillStock, $SeckillStock, 0, 1, 1, '2026-01-01 00:00:00', '2029-12-31 23:59:59')
on duplicate key update
    activity_name = values(activity_name),
    goods_id = values(goods_id),
    seckill_price = values(seckill_price),
    total_count = $SeckillStock,
    available_count = $SeckillStock,
    lock_count = 0,
    take_limit_count = 1,
    status = 1,
    start_time = '2026-01-01 00:00:00',
    end_time = '2029-12-31 23:59:59';
"@

    $keys = docker exec $RedisContainer redis-cli -p 6379 --scan --pattern "*$SeckillActivityId*" 2>$null
    foreach ($key in $keys) {
        if (-not [string]::IsNullOrWhiteSpace($key)) {
            docker exec $RedisContainer redis-cli -p 6379 del $key | Out-Null
        }
    }
}

if (-not (Test-HttpHealth $MarketHost)) {
    throw "Market service is not healthy: $MarketHost/actuator/health"
}

$seckillSteps = Convert-StepList $SeckillConcurrencySteps "SeckillConcurrencySteps"
$groupBuySteps = Convert-StepList $GroupBuyConcurrencySteps "GroupBuyConcurrencySteps"
$resultDir = Join-Path (Resolve-Path ".") ("docs\capacity-results\" + (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Force -Path $resultDir | Out-Null

if (-not $SkipSeed) {
    Reset-SeckillData
}

$runPrefix = "cap" + (Get-Date -Format "MMddHHmmss")
$summary = @()

Invoke-Pressure "seckill-query" 1000 100 $SeckillActivityId $SeckillGoodsId "${runPrefix}_query" (Join-Path $resultDir "seckill-query-100.json") $false

foreach ($concurrency in $seckillSteps) {
    $total = [Math]::Max($SeckillTotalPerStep, $concurrency)
    $file = Join-Path $resultDir ("seckill-lock-c{0}.json" -f $concurrency)
    Invoke-Pressure "seckill-lock" $total $concurrency $SeckillActivityId $SeckillGoodsId "${runPrefix}_sk${concurrency}" $file $true
    Start-Sleep -Seconds $WaitSeconds
    $invariantFile = Join-Path $resultDir ("invariants-after-seckill-c{0}.json" -f $concurrency)
    powershell -ExecutionPolicy Bypass -File scripts\pressure\check-invariants.ps1 `
        -SeckillActivityId $SeckillActivityId `
        -SeckillOrderShardCount $SeckillOrderShardCount `
        -StockBucketCount $StockBucketCount `
        -SkipGroupBuy `
        -Json | Tee-Object -FilePath $invariantFile
    if ($LASTEXITCODE -ne 0) {
        throw "Seckill invariant check failed after concurrency=$concurrency."
    }
    $summary += "seckill-lock concurrency=$concurrency total=$total invariant=pass"
}

$groupBuyPrefix = "${runPrefix}_gb"
if (-not $SkipGroupBuy) {
    $queryFile = Join-Path $resultDir "group-buy-query-c100.json"
    Invoke-Pressure "group-buy-query" 300 100 $GroupBuyActivityId $GroupBuyGoodsId "${groupBuyPrefix}_query" $queryFile $false
    foreach ($concurrency in $groupBuySteps) {
        $total = [Math]::Max($GroupBuyTotalPerStep, $concurrency)
        $file = Join-Path $resultDir ("group-buy-lock-c{0}.json" -f $concurrency)
        Invoke-Pressure "group-buy-lock" $total $concurrency $GroupBuyActivityId $GroupBuyGoodsId "${groupBuyPrefix}_${concurrency}" $file $false
        Start-Sleep -Seconds 3
    }
    $invariantFile = Join-Path $resultDir "invariants-after-group-buy.json"
    powershell -ExecutionPolicy Bypass -File scripts\pressure\check-invariants.ps1 `
        -SeckillActivityId $SeckillActivityId `
        -SeckillOrderShardCount $SeckillOrderShardCount `
        -StockBucketCount $StockBucketCount `
        -GroupBuyUserPrefix $groupBuyPrefix `
        -Json | Tee-Object -FilePath $invariantFile
    if ($LASTEXITCODE -ne 0) {
        throw "Group-buy invariant check failed."
    }
    $summary += "group-buy-lock prefix=$groupBuyPrefix invariant=pass"
}

$report = Join-Path $resultDir "report.md"
@"
# Local Pressure Matrix

- Time: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
- Market host: $MarketHost
- Seckill activity: $SeckillActivityId
- Seckill stock: $SeckillStock
- Seckill shard count: $SeckillOrderShardCount
- Group-buy activity: $GroupBuyActivityId

## Result

$($summary | ForEach-Object { "- $_" } | Out-String)

Artifacts are stored in this directory.
"@ | Set-Content -Path $report -Encoding UTF8

Write-Host "Pressure matrix results: $resultDir"
