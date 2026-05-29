param(
  [string]$ProcessName = "java",
  [string]$Match = "group-buy-market-app.jar",
  [int]$PauseSeconds = 10
)

$ErrorActionPreference = "Stop"
$process = Get-CimInstance Win32_Process | Where-Object { $_.Name -like "$ProcessName*" -and $_.CommandLine -like "*$Match*" } | Select-Object -First 1
if ($null -eq $process) {
  throw "process not found: $Match"
}
Write-Host "suspend process $($process.ProcessId) for $PauseSeconds seconds"
powershell -NoProfile -Command "Add-Type -Name Native -Namespace Win32 -MemberDefinition '[DllImport(\"ntdll.dll\")] public static extern int NtSuspendProcess(IntPtr processHandle); [DllImport(\"ntdll.dll\")] public static extern int NtResumeProcess(IntPtr processHandle);'; `$p=[System.Diagnostics.Process]::GetProcessById($($process.ProcessId)); [Win32.Native]::NtSuspendProcess(`$p.Handle); Start-Sleep -Seconds $PauseSeconds; [Win32.Native]::NtResumeProcess(`$p.Handle)"
Write-Host "http timeout drill finished"
