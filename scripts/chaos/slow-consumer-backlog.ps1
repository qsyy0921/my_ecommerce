param(
  [string]$RabbitContainer = "gbm-rabbitmq",
  [string]$MallJarMatch = "s-pay-mall-ddd-app.jar",
  [int]$MessageCount = 100,
  [int]$PauseSeconds = 15
)

$ErrorActionPreference = "Stop"
$process = Get-CimInstance Win32_Process | Where-Object { $_.Name -like "java*" -and $_.CommandLine -like "*$MallJarMatch*" } | Select-Object -First 1
if ($null -ne $process) {
  Write-Host "suspend mall process $($process.ProcessId) for $PauseSeconds seconds"
  Start-Job -ScriptBlock {
    param($targetPid,$seconds)
    powershell -NoProfile -Command "Add-Type -Name Native -Namespace Win32 -MemberDefinition '[DllImport(\"ntdll.dll\")] public static extern int NtSuspendProcess(IntPtr processHandle); [DllImport(\"ntdll.dll\")] public static extern int NtResumeProcess(IntPtr processHandle);'; `$p=[System.Diagnostics.Process]::GetProcessById($targetPid); [Win32.Native]::NtSuspendProcess(`$p.Handle); Start-Sleep -Seconds $seconds; [Win32.Native]::NtResumeProcess(`$p.Handle)"
  } -ArgumentList $process.ProcessId,$PauseSeconds | Out-Null
}

for ($i = 0; $i -lt $MessageCount; $i++) {
  $body = "{`"teamId`":`"chaos`",`"outTradeNoList`":[`"CHAOS$i`"]}"
  docker exec $RabbitContainer rabbitmqadmin publish exchange=group_buy_market_exchange routing_key=topic.team_success payload="$body" | Out-Null
}

Write-Host "published $MessageCount messages for slow consumer backlog drill"
