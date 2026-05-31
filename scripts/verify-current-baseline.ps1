param(
  [ValidateSet("docs-only", "market-domain", "market-compile", "mall-compile", "group-buy", "seckill", "mall-reconcile", "full-local")]
  [string]$ProfileName = "docs-only",
  [string]$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path
)

$ErrorActionPreference = "Stop"

$mavenExe = Join-Path $ProjectRoot ".tools\apache-maven-3.8.8\bin\mvn.cmd"
$marketPom = Join-Path $ProjectRoot "group-buy-market-master\pom.xml"
$mallPom = Join-Path $ProjectRoot "s-pay-mall-ddd-market-master\pom.xml"
$domainPurityScript = Join-Path $ProjectRoot "scripts\check-domain-purity.ps1"

function Invoke-NativeStep {
  param(
    [string]$Name,
    [scriptblock]$Command
  )

  Write-Host "==> $Name"
  & $Command
  $exitCode = $LASTEXITCODE
  if ($null -ne $exitCode -and $exitCode -ne 0) {
    throw "$Name failed with exit code $exitCode."
  }
}

function Invoke-MavenStep {
  param(
    [string]$Name,
    [string]$Pom,
    [string[]]$Arguments
  )

  if (-not (Test-Path $mavenExe)) {
    throw "Maven not found: $mavenExe"
  }
  if (-not (Test-Path $Pom)) {
    throw "Maven pom not found: $Pom"
  }

  Invoke-NativeStep $Name { & $mavenExe -f $Pom @Arguments }
}

function Invoke-DocsOnly {
  Invoke-NativeStep "git diff --check" { git diff --check }
  Invoke-NativeStep "SDD checklist anchors" {
    rg -n "Done List|TODO List|所有未完成任务清单|current-verification-baseline|verify-current-baseline" (Join-Path $ProjectRoot "docs\sdd")
  }
}

function Invoke-MarketDomain {
  Invoke-NativeStep "domain purity script" {
    powershell -NoProfile -ExecutionPolicy Bypass -File $domainPurityScript
  }
  Invoke-MavenStep "market domain guard tests" $marketPom @(
    "-q",
    "-pl",
    "group-buy-market-app",
    "-am",
    "-DskipTests=false",
    "-DfailIfNoTests=false",
    "-Dtest=cn.bugstack.test.architecture.DomainPurityTest,cn.bugstack.test.domain.shared.OrderStateMachineTest",
    "test"
  )
}

function Invoke-MarketCompile {
  Invoke-MavenStep "market compile" $marketPom @(
    "-q",
    "-pl",
    "group-buy-market-app",
    "-am",
    "-DskipTests",
    "compile"
  )
}

function Invoke-MallCompile {
  Invoke-MavenStep "mall compile" $mallPom @(
    "-q",
    "-pl",
    "s-pay-mall-ddd-app",
    "-am",
    "-DskipTests",
    "compile"
  )
}

function Invoke-GroupBuy {
  Invoke-MavenStep "group-buy domain tests" $marketPom @(
    "-q",
    "-pl",
    "group-buy-market-app",
    "-am",
    "-DskipTests=false",
    "-DfailIfNoTests=false",
    "-Dtest=cn.bugstack.test.domain.trade.TradeLockOrderServiceUnitTest,cn.bugstack.test.domain.trade.TradeRefundOrderServiceUnitTest",
    "test"
  )
}

function Invoke-Seckill {
  Invoke-MavenStep "seckill infrastructure tests" $marketPom @(
    "-q",
    "-pl",
    "group-buy-market-app",
    "-am",
    "-DskipTests=false",
    "-DfailIfNoTests=false",
    "-Dtest=cn.bugstack.test.infrastructure.seckill.SeckillOrderLockPortUnitTest,cn.bugstack.test.infrastructure.seckill.SeckillStockAvailabilityPortUnitTest,cn.bugstack.test.infrastructure.seckill.SeckillRateLimitPortUnitTest,cn.bugstack.test.infrastructure.seckill.SeckillSettlementPortUnitTest",
    "test"
  )
}

function Invoke-MallReconcile {
  Invoke-MavenStep "mall payment and reconcile tests" $mallPom @(
    "-q",
    "-pl",
    "s-pay-mall-ddd-app",
    "-am",
    "-DskipTests=false",
    "-DfailIfNoTests=false",
    "-Dtest=cn.bugstack.test.domain.OrderReconcileServiceReplayContractTest,cn.bugstack.test.domain.OrderServiceTest,cn.bugstack.test.infrastructure.message.MessageProducerRetrySupportUnitTest,cn.bugstack.test.infrastructure.message.MqProducerFailureRecorderUnitTest,cn.bugstack.test.infrastructure.reconcile.ReconcileOperationLogSupportUnitTest",
    "test"
  )
}

Set-Location $ProjectRoot

switch ($ProfileName) {
  "docs-only" {
    Invoke-DocsOnly
  }
  "market-domain" {
    Invoke-MarketDomain
  }
  "market-compile" {
    Invoke-MarketCompile
  }
  "mall-compile" {
    Invoke-MallCompile
  }
  "group-buy" {
    Invoke-GroupBuy
  }
  "seckill" {
    Invoke-Seckill
  }
  "mall-reconcile" {
    Invoke-MallReconcile
  }
  "full-local" {
    Invoke-DocsOnly
    Invoke-MarketDomain
    Invoke-MarketCompile
    Invoke-MallCompile
    Invoke-GroupBuy
    Invoke-Seckill
    Invoke-MallReconcile
  }
}

Write-Host "Verification profile passed: $ProfileName"
