param(
  [ValidateSet("redis-jitter", "rabbitmq-outage", "mysql-pause", "mysql-deadlock", "http-timeout", "slow-consumer-backlog")]
  [string]$Scenario = "redis-jitter",
  [string]$MallUrl = "http://127.0.0.1:8070",
  [string]$MarketUrl = "http://127.0.0.1:8091",
  [string]$ReportDir = "docs\chaos-reports"
)

$ErrorActionPreference = "Continue"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$reportPath = Join-Path $root $ReportDir
New-Item -ItemType Directory -Force -Path $reportPath | Out-Null
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$output = Join-Path $reportPath "chaos-report-$Scenario-$timestamp.md"

function Invoke-Capture {
  param(
    [string]$Title,
    [scriptblock]$Command
  )
  $lines = New-Object System.Collections.Generic.List[string]
  $lines.Add("## $Title")
  $lines.Add("")
  $lines.Add("````text")
  try {
    $result = & $Command 2>&1 | Out-String
    if ([string]::IsNullOrWhiteSpace($result)) {
      $result = "(empty)"
    }
    $lines.Add($result.TrimEnd())
  } catch {
    $lines.Add($_.Exception.Message)
  }
  $lines.Add("````")
  $lines.Add("")
  return $lines
}

function Read-Url {
  param([string]$Url)
  try {
    $response = Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 -Uri $Url
    return "HTTP $($response.StatusCode)`n$($response.Content.Substring(0, [Math]::Min(2000, $response.Content.Length)))"
  } catch {
    return "FAILED $Url`n$($_.Exception.Message)"
  }
}

function Append-Lines {
  param($Lines)
  foreach ($line in $Lines) {
    $report.Add($line)
  }
}

$scenarioFile = Join-Path $PSScriptRoot "$Scenario.ps1"
$report = New-Object System.Collections.Generic.List[string]
$now = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$report.Add("# Local Chaos Drill Report")
$report.Add("")
$report.Add("- Scenario: $Scenario")
$report.Add("- Time: $now")
$report.Add("- Machine: $env:COMPUTERNAME")
$report.Add("- Note: this report is generated from local Docker Desktop and local services. It verifies recovery behavior only, not production capacity.")
$report.Add("")

$section = Invoke-Capture -Title "Before Docker Status" -Command { docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' }
Append-Lines $section
$section = Invoke-Capture -Title "Before Mall Health" -Command { Read-Url "$MallUrl/actuator/health" }
Append-Lines $section
$section = Invoke-Capture -Title "Before Market Health" -Command { Read-Url "$MarketUrl/actuator/health" }
Append-Lines $section
$section = Invoke-Capture -Title "Before Mall Metrics Sample" -Command { Read-Url "$MallUrl/actuator/prometheus" }
Append-Lines $section

$report.Add("## Drill Execution")
$report.Add("")
$report.Add("````text")
if (Test-Path $scenarioFile) {
  $runOutput = powershell -ExecutionPolicy Bypass -File $scenarioFile 2>&1 | Out-String
  $report.Add($runOutput.TrimEnd())
} else {
  $report.Add("scenario file not found: $scenarioFile")
}
$report.Add("````")
$report.Add("")

Start-Sleep -Seconds 3
$section = Invoke-Capture -Title "After Docker Status" -Command { docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' }
Append-Lines $section
$section = Invoke-Capture -Title "After Mall Health" -Command { Read-Url "$MallUrl/actuator/health" }
Append-Lines $section
$section = Invoke-Capture -Title "After Market Health" -Command { Read-Url "$MarketUrl/actuator/health" }
Append-Lines $section
$section = Invoke-Capture -Title "After Mall Metrics Sample" -Command { Read-Url "$MallUrl/actuator/prometheus" }
Append-Lines $section

$report.Add("## Findings To Fill")
$report.Add("")
$report.Add("- Was the failure captured by metrics: TBD")
$report.Add("- Did services recover automatically: TBD")
$report.Add("- Were reconcile cases or compensation tasks created: TBD")
$report.Add("- Follow-up improvements: TBD")

$report | Set-Content -Path $output -Encoding UTF8
Write-Host "chaos report generated: $output"
