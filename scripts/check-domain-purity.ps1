param(
  [string]$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path
)

$ErrorActionPreference = "Stop"

$patterns = @(
  "org\.springframework",
  "javax\.annotation\.Resource",
  "@Service",
  "@Component",
  "@Resource",
  "@Autowired",
  "@Value",
  "ThreadPoolExecutor"
)

$domainPaths = @(
  (Join-Path $ProjectRoot "qsyy-commerce-market\group-buy-market-domain\src\main\java\cn\bugstack\domain"),
  (Join-Path $ProjectRoot "qsyy-commerce-mall\s-pay-mall-ddd-domain\src\main\java\cn\bugstack\domain")
)

$violations = @()
foreach ($path in $domainPaths) {
  if (Test-Path $path) {
    $result = rg -n ($patterns -join "|") $path 2>$null
    if ($LASTEXITCODE -eq 0 -and $result) {
      $violations += $result
    }
  }
}

if ($violations.Count -gt 0) {
  Write-Host "Domain purity check failed. Spring/container annotations found:" -ForegroundColor Red
  $violations | ForEach-Object { Write-Host $_ }
  exit 1
}

Write-Host "Domain purity check passed."
