param(
    [string]$OutputDir = "",
    [int]$DurationSeconds = 120,
    [int]$IntervalSeconds = 5,
    [string]$StopFile = "",
    [string[]]$ServiceUrls = @("http://127.0.0.1:8091", "http://127.0.0.1:8070"),
    [string[]]$DockerContainers = @("gbm-mysql", "gbm-redis", "gbm-rabbitmq", "gbm-jaeger"),
    [string]$MysqlContainer = "gbm-mysql",
    [string]$RedisContainer = "gbm-redis",
    [string]$RabbitContainer = "gbm-rabbitmq"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path (Resolve-Path ".").Path ("docs\capacity-results\" + (Get-Date -Format "yyyyMMdd-HHmmss") + "-watermark")
}
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$logicalProcessors = [Math]::Max(1, (Get-CimInstance Win32_ComputerSystem).NumberOfLogicalProcessors)
$previousJava = @{}
$samples = New-Object System.Collections.Generic.List[object]

function Invoke-NativeText([scriptblock]$Command) {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & $Command 2>$null
        $exitCode = $LASTEXITCODE
    } catch {
        $output = @()
        $exitCode = 1
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    return [ordered]@{
        output = @($output)
        exitCode = $exitCode
    }
}

function Convert-SizeToMb([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $null
    }
    $trimmed = $Value.Trim()
    if ($trimmed -notmatch '^([0-9.]+)\s*([A-Za-z]+)$') {
        return $null
    }
    $number = [double]$matches[1]
    $unit = $matches[2].ToLowerInvariant()
    switch ($unit) {
        "b" { return [Math]::Round($number / 1MB, 2) }
        "kb" { return [Math]::Round($number / 1024, 2) }
        "kib" { return [Math]::Round($number / 1024, 2) }
        "mb" { return [Math]::Round($number, 2) }
        "mib" { return [Math]::Round($number, 2) }
        "gb" { return [Math]::Round($number * 1024, 2) }
        "gib" { return [Math]::Round($number * 1024, 2) }
        default { return $null }
    }
}

function Get-ServiceNameFromCommand([string]$CommandLine) {
    if ($CommandLine -like "*group-buy-market-app.jar*") {
        return "group-buy-market"
    }
    if ($CommandLine -like "*s-pay-mall-ddd-app.jar*") {
        return "s-pay-mall-ddd"
    }
    return "java"
}

function Get-JavaProcesses {
    $now = Get-Date
    $rows = @()
    $processes = Get-CimInstance Win32_Process -Filter "name = 'java.exe'" |
            Where-Object { $_.CommandLine -like "*group-buy-market-app.jar*" -or $_.CommandLine -like "*s-pay-mall-ddd-app.jar*" }
    foreach ($procInfo in $processes) {
        try {
            $proc = Get-Process -Id $procInfo.ProcessId -ErrorAction Stop
            $cpuPct = $null
            if ($previousJava.ContainsKey($proc.Id)) {
                $previous = $previousJava[$proc.Id]
                $elapsed = [Math]::Max(0.001, ($now - $previous.timestamp).TotalSeconds)
                $deltaCpu = [Math]::Max(0, $proc.CPU - $previous.cpuSeconds)
                $cpuPct = [Math]::Round(($deltaCpu / $elapsed / $logicalProcessors) * 100, 2)
            }
            $previousJava[$proc.Id] = @{
                timestamp = $now
                cpuSeconds = $proc.CPU
            }
            $rows += [ordered]@{
                pid = $proc.Id
                service = Get-ServiceNameFromCommand $procInfo.CommandLine
                cpuPct = $cpuPct
                cpuSeconds = [Math]::Round($proc.CPU, 2)
                workingSetMb = [Math]::Round($proc.WorkingSet64 / 1MB, 2)
                privateMemoryMb = [Math]::Round($proc.PrivateMemorySize64 / 1MB, 2)
                threadCount = $proc.Threads.Count
            }
        } catch {
            $rows += [ordered]@{
                pid = $procInfo.ProcessId
                service = Get-ServiceNameFromCommand $procInfo.CommandLine
                error = $_.Exception.Message
            }
        }
    }
    return $rows
}

function Get-DockerStats {
    $rows = @()
    $existingResult = Invoke-NativeText { docker ps --format "{{.Names}}" }
    if ($existingResult.exitCode -ne 0) {
        return @([ordered]@{ skipped = $true; reason = "docker unavailable" })
    }
    $existing = $existingResult.output
    foreach ($container in $DockerContainers) {
        if ($existing -notcontains $container) {
            $rows += [ordered]@{ name = $container; skipped = $true; reason = "container not running" }
            continue
        }
        $result = Invoke-NativeText { docker stats --no-stream --format "{{json .}}" $container }
        $line = $result.output | Select-Object -First 1
        if ($result.exitCode -ne 0 -or [string]::IsNullOrWhiteSpace($line)) {
            $rows += [ordered]@{ name = $container; error = "docker stats failed" }
            continue
        }
        $stat = $line | ConvertFrom-Json
        $memUsed = (($stat.MemUsage -as [string]) -split "/")[0].Trim()
        $rows += [ordered]@{
            name = $stat.Name
            cpuPct = [double](($stat.CPUPerc -as [string]).Replace("%", ""))
            memUsageRaw = $stat.MemUsage
            memUsedMb = Convert-SizeToMb $memUsed
            netIO = $stat.NetIO
            blockIO = $stat.BlockIO
            pids = $stat.PIDs
        }
    }
    return $rows
}

function Convert-KeyValueLines([string[]]$Lines) {
    $map = [ordered]@{}
    foreach ($line in $Lines) {
        $trimmed = ($line -as [string]).Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#") -or -not $trimmed.Contains(":")) {
            continue
        }
        $idx = $trimmed.IndexOf(":")
        $key = $trimmed.Substring(0, $idx)
        $value = $trimmed.Substring($idx + 1)
        $map[$key] = $value
    }
    return $map
}

function Get-RedisSnapshot {
    $existingResult = Invoke-NativeText { docker ps --format "{{.Names}}" }
    $existing = $existingResult.output
    if ($existing -notcontains $RedisContainer) {
        return [ordered]@{ skipped = $true; reason = "container not running" }
    }
    $result = Invoke-NativeText { docker exec $RedisContainer redis-cli -p 6379 info }
    $info = $result.output
    if ($result.exitCode -ne 0) {
        return [ordered]@{ error = "redis info failed" }
    }
    $map = Convert-KeyValueLines $info
    return [ordered]@{
        usedMemoryMb = if ($map.Contains("used_memory")) { [Math]::Round(([double]$map["used_memory"]) / 1MB, 2) } else { $null }
        connectedClients = if ($map.Contains("connected_clients")) { [int]$map["connected_clients"] } else { $null }
        instantaneousOpsPerSec = if ($map.Contains("instantaneous_ops_per_sec")) { [int]$map["instantaneous_ops_per_sec"] } else { $null }
        keyspaceHits = if ($map.Contains("keyspace_hits")) { [long]$map["keyspace_hits"] } else { $null }
        keyspaceMisses = if ($map.Contains("keyspace_misses")) { [long]$map["keyspace_misses"] } else { $null }
        rejectedConnections = if ($map.Contains("rejected_connections")) { [long]$map["rejected_connections"] } else { $null }
        blockedClients = if ($map.Contains("blocked_clients")) { [int]$map["blocked_clients"] } else { $null }
    }
}

function Get-MysqlSnapshot {
    $existingResult = Invoke-NativeText { docker ps --format "{{.Names}}" }
    $existing = $existingResult.output
    if ($existing -notcontains $MysqlContainer) {
        return [ordered]@{ skipped = $true; reason = "container not running" }
    }
    $names = "Threads_connected','Threads_running','Questions','Slow_queries','Innodb_row_lock_waits','Created_tmp_disk_tables','Connections','Uptime"
    $sql = "show global status where variable_name in ('$names');"
    $result = Invoke-NativeText { docker exec $MysqlContainer mysql -N -B -uroot -p123456 -e $sql }
    $rows = $result.output
    if ($result.exitCode -ne 0) {
        return [ordered]@{ error = "mysql status failed" }
    }
    $map = [ordered]@{}
    foreach ($row in $rows) {
        $parts = ($row -as [string]) -split "`t"
        if ($parts.Count -ge 2) {
            $map[$parts[0]] = $parts[1]
        }
    }
    return [ordered]@{
        threadsConnected = if ($map.Contains("Threads_connected")) { [int]$map["Threads_connected"] } else { $null }
        threadsRunning = if ($map.Contains("Threads_running")) { [int]$map["Threads_running"] } else { $null }
        questions = if ($map.Contains("Questions")) { [long]$map["Questions"] } else { $null }
        slowQueries = if ($map.Contains("Slow_queries")) { [long]$map["Slow_queries"] } else { $null }
        innodbRowLockWaits = if ($map.Contains("Innodb_row_lock_waits")) { [long]$map["Innodb_row_lock_waits"] } else { $null }
        createdTmpDiskTables = if ($map.Contains("Created_tmp_disk_tables")) { [long]$map["Created_tmp_disk_tables"] } else { $null }
        connections = if ($map.Contains("Connections")) { [long]$map["Connections"] } else { $null }
        uptimeSeconds = if ($map.Contains("Uptime")) { [long]$map["Uptime"] } else { $null }
    }
}

function Get-RabbitSnapshot {
    $existingResult = Invoke-NativeText { docker ps --format "{{.Names}}" }
    $existing = $existingResult.output
    if ($existing -notcontains $RabbitContainer) {
        return [ordered]@{ skipped = $true; reason = "container not running" }
    }
    $result = Invoke-NativeText { docker exec $RabbitContainer rabbitmqctl list_queues --formatter json name messages messages_ready messages_unacknowledged consumers }
    $json = $result.output
    if ($result.exitCode -ne 0 -or [string]::IsNullOrWhiteSpace(($json | Out-String))) {
        return [ordered]@{ error = "rabbitmqctl list_queues failed" }
    }
    $queues = ($json | Out-String | ConvertFrom-Json)
    $totalMessages = 0
    $totalReady = 0
    $totalUnacked = 0
    foreach ($queue in $queues) {
        $totalMessages += [int]$queue.messages
        $totalReady += [int]$queue.messages_ready
        $totalUnacked += [int]$queue.messages_unacknowledged
    }
    return [ordered]@{
        queueCount = @($queues).Count
        totalMessages = $totalMessages
        totalReady = $totalReady
        totalUnacked = $totalUnacked
        queues = $queues
    }
}

function Get-ActuatorMetric([string]$BaseUrl, [string]$MetricName) {
    try {
        $metric = Invoke-RestMethod -Uri "$BaseUrl/actuator/metrics/$MetricName" -TimeoutSec 3
        return [double]($metric.measurements | Select-Object -First 1).value
    } catch {
        return $null
    }
}

function Get-ActuatorSnapshot {
    $rows = @()
    foreach ($url in $ServiceUrls) {
        $rows += [ordered]@{
            baseUrl = $url
            processCpuUsage = Get-ActuatorMetric $url "process.cpu.usage"
            systemCpuUsage = Get-ActuatorMetric $url "system.cpu.usage"
            jvmMemoryUsed = Get-ActuatorMetric $url "jvm.memory.used"
            jvmGcPauseCount = Get-ActuatorMetric $url "jvm.gc.pause"
            tomcatThreadsBusy = Get-ActuatorMetric $url "tomcat.threads.busy"
            hikaricpConnectionsActive = Get-ActuatorMetric $url "hikaricp.connections.active"
        }
    }
    return $rows
}

function Max-Value($Values) {
    $filtered = @($Values | Where-Object { $null -ne $_ })
    if ($filtered.Count -eq 0) {
        return $null
    }
    return ($filtered | Measure-Object -Maximum).Maximum
}

function Build-Summary([object[]]$AllSamples) {
    $javaRows = @($AllSamples | ForEach-Object { $_.java } | ForEach-Object { $_ })
    $containerRows = @($AllSamples | ForEach-Object { $_.docker } | ForEach-Object { $_ })
    $actuatorRows = @($AllSamples | ForEach-Object { $_.actuator } | ForEach-Object { $_ })
    return [ordered]@{
        sampleCount = $AllSamples.Count
        startedAt = if ($AllSamples.Count -gt 0) { $AllSamples[0].timestamp } else { $null }
        endedAt = if ($AllSamples.Count -gt 0) { $AllSamples[$AllSamples.Count - 1].timestamp } else { $null }
        java = [ordered]@{
            maxCpuPct = Max-Value ($javaRows | ForEach-Object { $_.cpuPct })
            maxWorkingSetMb = Max-Value ($javaRows | ForEach-Object { $_.workingSetMb })
            maxPrivateMemoryMb = Max-Value ($javaRows | ForEach-Object { $_.privateMemoryMb })
            maxThreadCount = Max-Value ($javaRows | ForEach-Object { $_.threadCount })
        }
        containers = [ordered]@{
            maxCpuPct = Max-Value ($containerRows | ForEach-Object { $_.cpuPct })
            maxMemUsedMb = Max-Value ($containerRows | ForEach-Object { $_.memUsedMb })
        }
        redis = [ordered]@{
            maxUsedMemoryMb = Max-Value ($AllSamples | ForEach-Object { $_.redis.usedMemoryMb })
            maxOpsPerSec = Max-Value ($AllSamples | ForEach-Object { $_.redis.instantaneousOpsPerSec })
            maxConnectedClients = Max-Value ($AllSamples | ForEach-Object { $_.redis.connectedClients })
            maxBlockedClients = Max-Value ($AllSamples | ForEach-Object { $_.redis.blockedClients })
        }
        mysql = [ordered]@{
            maxThreadsConnected = Max-Value ($AllSamples | ForEach-Object { $_.mysql.threadsConnected })
            maxThreadsRunning = Max-Value ($AllSamples | ForEach-Object { $_.mysql.threadsRunning })
            maxSlowQueries = Max-Value ($AllSamples | ForEach-Object { $_.mysql.slowQueries })
            maxInnodbRowLockWaits = Max-Value ($AllSamples | ForEach-Object { $_.mysql.innodbRowLockWaits })
        }
        rabbitmq = [ordered]@{
            maxMessages = Max-Value ($AllSamples | ForEach-Object { $_.rabbitmq.totalMessages })
            maxReady = Max-Value ($AllSamples | ForEach-Object { $_.rabbitmq.totalReady })
            maxUnacked = Max-Value ($AllSamples | ForEach-Object { $_.rabbitmq.totalUnacked })
        }
        actuator = [ordered]@{
            maxProcessCpuUsage = Max-Value ($actuatorRows | ForEach-Object { $_.processCpuUsage })
            maxSystemCpuUsage = Max-Value ($actuatorRows | ForEach-Object { $_.systemCpuUsage })
            maxJvmMemoryUsedBytes = Max-Value ($actuatorRows | ForEach-Object { $_.jvmMemoryUsed })
            maxTomcatThreadsBusy = Max-Value ($actuatorRows | ForEach-Object { $_.tomcatThreadsBusy })
            maxHikariConnectionsActive = Max-Value ($actuatorRows | ForEach-Object { $_.hikaricpConnectionsActive })
        }
    }
}

function Write-Report($Summary, [string]$Path) {
@"
# Resource Watermark Report

- Generated at: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
- Sample count: $($Summary.sampleCount)
- Started at: $($Summary.startedAt)
- Ended at: $($Summary.endedAt)

## JVM Processes

- Max CPU percent: $($Summary.java.maxCpuPct)
- Max working set MB: $($Summary.java.maxWorkingSetMb)
- Max private memory MB: $($Summary.java.maxPrivateMemoryMb)
- Max thread count: $($Summary.java.maxThreadCount)

## Docker Containers

- Max CPU percent: $($Summary.containers.maxCpuPct)
- Max memory used MB: $($Summary.containers.maxMemUsedMb)

## Redis

- Max used memory MB: $($Summary.redis.maxUsedMemoryMb)
- Max ops/sec: $($Summary.redis.maxOpsPerSec)
- Max connected clients: $($Summary.redis.maxConnectedClients)
- Max blocked clients: $($Summary.redis.maxBlockedClients)

## MySQL

- Max threads connected: $($Summary.mysql.maxThreadsConnected)
- Max threads running: $($Summary.mysql.maxThreadsRunning)
- Max slow queries: $($Summary.mysql.maxSlowQueries)
- Max InnoDB row lock waits: $($Summary.mysql.maxInnodbRowLockWaits)

## RabbitMQ

- Max messages: $($Summary.rabbitmq.maxMessages)
- Max ready: $($Summary.rabbitmq.maxReady)
- Max unacked: $($Summary.rabbitmq.maxUnacked)

## Actuator

- Max process CPU usage: $($Summary.actuator.maxProcessCpuUsage)
- Max system CPU usage: $($Summary.actuator.maxSystemCpuUsage)
- Max JVM memory used bytes: $($Summary.actuator.maxJvmMemoryUsedBytes)
- Max Tomcat busy threads: $($Summary.actuator.maxTomcatThreadsBusy)
- Max Hikari active connections: $($Summary.actuator.maxHikariConnectionsActive)

## Boundary

This report is a local workstation watermark. It is useful for finding obvious bottlenecks during local pressure tests, but it is not a production capacity certificate.
"@ | Set-Content -Path $Path -Encoding UTF8
}

$deadline = (Get-Date).AddSeconds($DurationSeconds)
do {
    $sample = [ordered]@{
        timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss.fff")
        java = @(Get-JavaProcesses)
        docker = @(Get-DockerStats)
        redis = Get-RedisSnapshot
        mysql = Get-MysqlSnapshot
        rabbitmq = Get-RabbitSnapshot
        actuator = @(Get-ActuatorSnapshot)
    }
    $samples.Add($sample) | Out-Null
    if (-not [string]::IsNullOrWhiteSpace($StopFile) -and (Test-Path $StopFile)) {
        break
    }
    Start-Sleep -Seconds $IntervalSeconds
} while ((Get-Date) -lt $deadline)

$sampleArray = @($samples.ToArray())
$summary = Build-Summary -AllSamples $sampleArray
$samplesPath = Join-Path $OutputDir "resource-samples.json"
$summaryPath = Join-Path $OutputDir "resource-summary.json"
$reportPath = Join-Path $OutputDir "resource-watermark-report.md"

$sampleArray | ConvertTo-Json -Depth 12 | Set-Content -Path $samplesPath -Encoding UTF8
$summary | ConvertTo-Json -Depth 8 | Set-Content -Path $summaryPath -Encoding UTF8
Write-Report $summary $reportPath

Write-Host "Resource watermark samples: $samplesPath"
Write-Host "Resource watermark summary: $summaryPath"
Write-Host "Resource watermark report: $reportPath"
