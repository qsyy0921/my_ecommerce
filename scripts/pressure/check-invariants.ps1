param(
    [long]$SeckillActivityId = 990700,
    [int]$SeckillOrderShardCount = 1,
    [int]$StockBucketCount = 64,
    [string]$GroupBuyUserPrefix = "",
    [string]$MysqlContainer = "gbm-mysql",
    [string]$RedisContainer = "gbm-redis",
    [switch]$SkipSeckill,
    [switch]$SkipGroupBuy,
    [switch]$Json
)

$ErrorActionPreference = "Stop"

function Invoke-MysqlScalar([string]$Sql) {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $output = docker exec $MysqlContainer mysql -N -B -uroot -p123456 group_buy_market -e $Sql 2>$null
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    if ($exitCode -ne 0) {
        throw "MySQL query failed: $Sql"
    }
    if ($null -eq $output) {
        return ""
    }
    return (($output | Select-Object -First 1) -as [string]).Trim()
}

function Test-MysqlTable([string]$TableName) {
    $escaped = $TableName.Replace("'", "''")
    return [int](Invoke-MysqlScalar "select count(1) from information_schema.tables where table_schema = database() and table_name = '$escaped';") -gt 0
}

function Get-OrderTables([int]$ShardCount) {
    if ($ShardCount -le 1) {
        return @("seckill_order")
    }
    $tables = @()
    for ($i = 0; $i -lt $ShardCount; $i++) {
        $tableName = "seckill_order_{0:D2}" -f $i
        if (Test-MysqlTable $tableName) {
            $tables += $tableName
        }
    }
    if ($tables.Count -eq 0) {
        throw "No seckill shard tables found. Check SeckillOrderShardCount=$ShardCount."
    }
    return $tables
}

function Get-SeckillOrderUnionSql([long]$ActivityId, [string[]]$Tables) {
    $parts = @()
    foreach ($table in $Tables) {
        $parts += "select user_id, out_trade_no, activity_id, status from ``$table`` where activity_id = $ActivityId"
    }
    return ($parts -join " union all ")
}

function Get-RedisInt([string]$Key) {
    $value = docker exec $RedisContainer redis-cli -p 6379 get $Key 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Redis query failed: $Key"
    }
    if ([string]::IsNullOrWhiteSpace($value)) {
        return 0
    }
    return [int]$value
}

function Test-SeckillInvariant {
    $tables = Get-OrderTables $SeckillOrderShardCount
    $unionSql = Get-SeckillOrderUnionSql $SeckillActivityId $tables
    $activity = Invoke-MysqlScalar "select concat(total_count, ',', available_count, ',', lock_count) from seckill_activity where activity_id = $SeckillActivityId;"
    if ([string]::IsNullOrWhiteSpace($activity)) {
        throw "Seckill activity $SeckillActivityId not found."
    }
    $parts = $activity.Split(',')
    $totalCount = [int]$parts[0]
    $availableCount = [int]$parts[1]
    $lockCount = [int]$parts[2]
    $activeOrders = [int](Invoke-MysqlScalar "select count(1) from ($unionSql) t where t.status in (0, 1);")
    $duplicateUserActivity = [int](Invoke-MysqlScalar "select count(1) from (select user_id, activity_id, count(1) c from ($unionSql) t group by user_id, activity_id having c > 1) d;")
    $duplicateUserOutTrade = [int](Invoke-MysqlScalar "select count(1) from (select user_id, out_trade_no, count(1) c from ($unionSql) t group by user_id, out_trade_no having c > 1) d;")
    $redisAvailable = 0
    for ($i = 0; $i -lt $StockBucketCount; $i++) {
        $redisAvailable += Get-RedisInt "seckill:stock:$SeckillActivityId`:$i"
    }
    $expectedAvailable = [Math]::Max($totalCount - $activeOrders, 0)
    $passed = $availableCount -eq $expectedAvailable -and
              $lockCount -eq $activeOrders -and
              $redisAvailable -eq $expectedAvailable -and
              $duplicateUserActivity -eq 0 -and
              $duplicateUserOutTrade -eq 0
    return [ordered]@{
        name = "seckill"
        passed = $passed
        activityId = $SeckillActivityId
        orderTables = $tables
        totalCount = $totalCount
        dbAvailableCount = $availableCount
        dbLockCount = $lockCount
        activeOrderCount = $activeOrders
        redisAvailableCount = $redisAvailable
        expectedAvailableCount = $expectedAvailable
        duplicateUserActivity = $duplicateUserActivity
        duplicateUserOutTrade = $duplicateUserOutTrade
    }
}

function Test-GroupBuyInvariant {
    if ([string]::IsNullOrWhiteSpace($GroupBuyUserPrefix)) {
        return [ordered]@{
            name = "group-buy"
            skipped = $true
            reason = "GroupBuyUserPrefix is empty."
        }
    }
    $prefix = $GroupBuyUserPrefix.Replace("'", "''")
    $teamMismatch = [int](Invoke-MysqlScalar @"
select count(1)
from (
    select o.team_id,
           o.target_count,
           o.lock_count,
           o.complete_count,
           sum(case when l.status in (0, 1) then 1 else 0 end) active_count,
           sum(case when l.status = 1 then 1 else 0 end) complete_order_count
    from group_buy_order o
    join group_buy_order_list l on o.team_id = l.team_id
    where o.team_id in (
        select distinct team_id
        from group_buy_order_list
        where user_id like '$prefix%'
    )
    group by o.team_id, o.target_count, o.lock_count, o.complete_count
    having o.lock_count <> active_count
       or o.complete_count <> complete_order_count
       or o.lock_count > o.target_count
       or o.complete_count > o.lock_count
) t;
"@)
    $duplicateUserOutTrade = [int](Invoke-MysqlScalar @"
select count(1)
from (
    select user_id, out_trade_no, count(1) c
    from group_buy_order_list
    where user_id like '$prefix%'
    group by user_id, out_trade_no
    having c > 1
) t;
"@)
    $teamCount = [int](Invoke-MysqlScalar "select count(distinct team_id) from group_buy_order_list where user_id like '$prefix%';")
    $orderCount = [int](Invoke-MysqlScalar "select count(1) from group_buy_order_list where user_id like '$prefix%';")
    $passed = $teamMismatch -eq 0 -and $duplicateUserOutTrade -eq 0
    return [ordered]@{
        name = "group-buy"
        passed = $passed
        userPrefix = $GroupBuyUserPrefix
        teamCount = $teamCount
        orderCount = $orderCount
        teamMismatch = $teamMismatch
        duplicateUserOutTrade = $duplicateUserOutTrade
    }
}

$checks = @()
if (-not $SkipSeckill) {
    $checks += Test-SeckillInvariant
}
if (-not $SkipGroupBuy) {
    $checks += Test-GroupBuyInvariant
}

$passed = ($checks | Where-Object { $_.Contains("passed") -and $_.passed -eq $false }).Count -eq 0
$result = [ordered]@{
    passed = $passed
    checkedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    checks = $checks
}

if ($Json) {
    $result | ConvertTo-Json -Depth 8
} else {
    $result | ConvertTo-Json -Depth 8
}

if (-not $passed) {
    exit 1
}
