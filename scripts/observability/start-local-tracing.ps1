param(
    [string]$ComposeFile = "docs\dev-ops\docker-compose-tracing.yml"
)

$ErrorActionPreference = "Stop"

function Invoke-DockerCompose([string[]]$ComposeArgs) {
    try {
        $dockerArgs = @("compose") + $ComposeArgs
        & docker $dockerArgs
        if ($LASTEXITCODE -ne 0) {
            throw "docker compose failed with exit code $LASTEXITCODE"
        }
        return
    } catch {
        if (Get-Command docker-compose -ErrorAction SilentlyContinue) {
            & docker-compose @ComposeArgs
            if ($LASTEXITCODE -ne 0) {
                throw "docker-compose failed with exit code $LASTEXITCODE"
            }
            return
        }
        throw
    }
}

if (-not (Test-Path $ComposeFile)) {
    throw "Compose file not found: $ComposeFile"
}

Invoke-DockerCompose @("-f", $ComposeFile, "up", "-d")

$deadline = (Get-Date).AddSeconds(45)
do {
    try {
        $services = Invoke-RestMethod -Uri "http://127.0.0.1:16686/api/services" -TimeoutSec 3
        Write-Host "Jaeger is ready: http://127.0.0.1:16686"
        Write-Host "Known services: $($services.data -join ', ')"
        return
    } catch {
        Start-Sleep -Seconds 2
    }
} while ((Get-Date) -lt $deadline)

throw "Jaeger did not become ready on http://127.0.0.1:16686 within 45 seconds."
